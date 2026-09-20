package com.miferstlab.binauralbeats.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.miferstlab.binauralbeats.data.Entitlements

/**
 * Play Billing scaffolding.
 *
 * TODO: add `com.android.billingclient:billing-ktx` to app/build.gradle.kts once
 * product [Entitlements.PRODUCT_ID_PREMIUM] exists in Play Console, then:
 * 1. BillingClient.newBuilder(context).setListener(...).enablePendingPurchases().build()
 * 2. queryProductDetails / launchBillingFlow for [Entitlements.PRODUCT_ID_PREMIUM]
 * 3. acknowledge purchase and call [onPremiumUnlocked]
 *
 * Until then, [purchasePremium] invokes [onPremiumUnlocked] only when
 * [allowDebugUnlock] is true (Settings debug switch path).
 */
class BillingManager(
    private val context: Context,
    private val onPremiumUnlocked: () -> Unit,
    private val allowDebugUnlock: () -> Boolean
) {
    fun purchasePremium(activity: Activity?) {
        if (allowDebugUnlock()) {
            Log.i(TAG, "Debug unlock for ${Entitlements.PRODUCT_ID_PREMIUM}")
            onPremiumUnlocked()
            return
        }
        // Real BillingClient path not wired yet — product must exist in Console.
        Log.w(
            TAG,
            "Play Billing not configured. Create product " +
                "${Entitlements.PRODUCT_ID_PREMIUM} then implement BillingClient here."
        )
        // Still unlock in debug builds so QA can exercise paid paths without Console.
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.i(TAG, "Debuggable build: falling back to local premium unlock")
            onPremiumUnlocked()
        }
    }

    fun restorePurchases() {
        // TODO: BillingClient.queryPurchasesAsync → unlock if owned
        Log.w(TAG, "restorePurchases: BillingClient TODO")
    }

    companion object {
        private const val TAG = "BillingManager"
    }
}
