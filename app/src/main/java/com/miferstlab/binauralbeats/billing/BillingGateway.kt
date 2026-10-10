package com.miferstlab.binauralbeats.billing

import android.app.Activity

/** Purchase state, mirrors Play's states we care about. */
enum class OwnedState { PURCHASED, PENDING }

/** The two base plans of the Premium subscription. */
enum class PremiumPlan(val basePlanId: String) {
    MONTHLY("monthly"),
    YEARLY("yearly")
}

/**
 * One purchase of the Premium subscription, independent of the Play Billing classes.
 * Play only returns non-expired subscriptions from queryPurchases, so a PURCHASED entry means
 * "active" (including canceled-but-not-yet-expired).
 */
data class OwnedPurchase(
    val purchaseToken: String,
    val state: OwnedState,
    val acknowledged: Boolean
)

enum class GatewayCode { OK, USER_CANCELED, ALREADY_OWNED, UNAVAILABLE, ERROR }

/**
 * What the paywall shows for one base plan. [formattedPrice] is the recurring price from Play
 * (never hardcoded); [trialDays] is non-null when the user is eligible for a free-trial offer.
 */
data class PlanInfo(
    val plan: PremiumPlan,
    val formattedPrice: String,
    val trialDays: Int?
)

sealed class DetailsResult {
    data class Found(val plans: List<PlanInfo>) : DetailsResult()
    /** Billing works but the subscription does not exist / is not active (not created in Play Console yet). */
    object NotFound : DetailsResult()
    object Error : DetailsResult()
}

sealed class OwnedResult {
    data class Success(val purchases: List<OwnedPurchase>) : OwnedResult()
    object Error : OwnedResult()
}

/**
 * Thin seam over Google's BillingClient so [BillingManager] can be unit-tested with a fake.
 * All callbacks arrive on the main thread.
 */
interface BillingGateway {
    var updatesListener: ((GatewayCode, List<OwnedPurchase>) -> Unit)?

    fun connect(onResult: (Boolean) -> Unit, onDisconnected: () -> Unit)

    /** Loads the subscription (SUBS) and its offers for both base plans. */
    fun queryPremiumDetails(onResult: (DetailsResult) -> Unit)

    /** Active subscriptions for the Premium product only (queryPurchasesAsync SUBS). */
    fun queryOwned(onResult: (OwnedResult) -> Unit)

    /**
     * Needs a successful [queryPremiumDetails] first. With [useTrial] the free-trial offer is used
     * when the user is eligible, otherwise the plain base-plan offer. False if the flow cannot start.
     */
    fun launchPurchase(activity: Activity, plan: PremiumPlan, useTrial: Boolean): Boolean

    fun acknowledge(purchaseToken: String, onResult: (Boolean) -> Unit)

    fun end()
}
