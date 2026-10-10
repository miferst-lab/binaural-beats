package com.miferstlab.binauralbeats.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.miferstlab.binauralbeats.data.Entitlements

/** Real Google Play implementation of [BillingGateway]. */
class PlayBillingGateway(context: Context) : BillingGateway, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    override var updatesListener: ((GatewayCode, List<OwnedPurchase>) -> Unit)? = null

    override fun connect(onResult: (Boolean) -> Unit, onDisconnected: () -> Unit) {
        client?.endConnection()
        val c = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        var answered = false
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (answered) return
                answered = true
                onResult(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                if (client !== c) return
                if (!answered) {
                    answered = true
                    onResult(false)
                } else {
                    onDisconnected()
                }
            }
        })
    }

    override fun queryPremiumDetails(onResult: (DetailsResult) -> Unit) {
        val c = client?.takeIf { it.isReady } ?: return onResult(DetailsResult.Error)
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(Entitlements.PRODUCT_ID_PREMIUM)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onResult(DetailsResult.Error)
                return@queryProductDetailsAsync
            }
            val found = queryResult.productDetailsList
                .firstOrNull { it.productId == Entitlements.PRODUCT_ID_PREMIUM }
            val offers = found?.subscriptionOfferDetails.orEmpty()
            val plans = PremiumPlan.values().mapNotNull { plan ->
                val forPlan = offers.filter { it.basePlanId == plan.basePlanId }
                val trial = forPlan.firstOrNull { trialDaysOf(it) != null }
                val plain = forPlan.firstOrNull { it.offerId == null && trialDaysOf(it) == null }
                    ?: forPlan.firstOrNull { trialDaysOf(it) == null }
                val priceSource = plain ?: trial ?: return@mapNotNull null
                val price = recurringPrice(priceSource) ?: return@mapNotNull null
                offerTokens[plan] = OfferTokens(plain?.offerToken, trial?.offerToken)
                PlanInfo(plan, price, trial?.let { trialDaysOf(it) })
            }
            if (found == null || plans.isEmpty()) {
                details = null
                offerTokens.clear()
                onResult(DetailsResult.NotFound)
            } else {
                details = found
                onResult(DetailsResult.Found(plans))
            }
        }
    }

    private class OfferTokens(val plain: String?, val trial: String?)
    private val offerTokens = mutableMapOf<PremiumPlan, OfferTokens>()

    /** Recurring (last, non-free) pricing phase = the price the user pays after any trial. */
    private fun recurringPrice(o: ProductDetails.SubscriptionOfferDetails): String? =
        o.pricingPhases.pricingPhaseList.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice

    /** Days of the free-trial phase (first phase with price 0), or null if the offer has none. */
    private fun trialDaysOf(o: ProductDetails.SubscriptionOfferDetails): Int? {
        val phase = o.pricingPhases.pricingPhaseList.firstOrNull { it.priceAmountMicros == 0L }
            ?: return null
        return parseIsoDays(phase.billingPeriod)
    }

    /** "P7D" -> 7, "P1W" -> 7, "P1M" -> 30 (display only). */
    private fun parseIsoDays(period: String): Int {
        val m = Regex("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?").matchEntire(period)
        fun g(i: Int) = m?.groupValues?.getOrNull(i)?.toIntOrNull() ?: 0
        val days = g(1) * 365 + g(2) * 30 + g(3) * 7 + g(4)
        return if (days > 0) days else 7
    }

    override fun queryOwned(onResult: (OwnedResult) -> Unit) {
        val c = client?.takeIf { it.isReady } ?: return onResult(OwnedResult.Error)
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onResult(OwnedResult.Success(map(purchases)))
            } else {
                onResult(OwnedResult.Error)
            }
        }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan, useTrial: Boolean): Boolean {
        val c = client?.takeIf { it.isReady } ?: return false
        val d = details ?: return false
        val tokens = offerTokens[plan] ?: return false
        val token = (if (useTrial) tokens.trial ?: tokens.plain else tokens.plain ?: tokens.trial)
            ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d)
                        .setOfferToken(token)
                        .build()
                )
            )
            .build()
        return c.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun acknowledge(purchaseToken: String, onResult: (Boolean) -> Unit) {
        val c = client?.takeIf { it.isReady } ?: return onResult(false)
        c.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        ) { result -> onResult(result.responseCode == BillingClient.BillingResponseCode.OK) }
    }

    override fun end() {
        client?.endConnection()
        client = null
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val code = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> GatewayCode.OK
            BillingClient.BillingResponseCode.USER_CANCELED -> GatewayCode.USER_CANCELED
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> GatewayCode.ALREADY_OWNED
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> GatewayCode.UNAVAILABLE
            else -> GatewayCode.ERROR
        }
        updatesListener?.invoke(code, map(purchases.orEmpty()))
    }

    private fun map(purchases: List<Purchase>): List<OwnedPurchase> = purchases
        .filter { Entitlements.PRODUCT_ID_PREMIUM in it.products }
        .mapNotNull { p ->
            val state = when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> OwnedState.PURCHASED
                Purchase.PurchaseState.PENDING -> OwnedState.PENDING
                else -> null
            }
            state?.let { OwnedPurchase(p.purchaseToken, it, p.isAcknowledged) }
        }
}
