package com.miferstlab.binauralbeats.billing

import android.app.Activity

/** What the paywall should show about Play Billing. */
enum class BillingStatus {
    /** Connecting / loading product details. */
    LOADING,
    /** Plans/prices loaded, purchase can be started. */
    READY,
    /** Play unavailable, offline, or product not (yet) created in Play Console. */
    UNAVAILABLE,
    /** Purchase is pending (e.g. cash payment); Premium is NOT unlocked yet. */
    PENDING,
    /** The last purchase attempt failed. */
    FAILED
}

data class BillingUiState(
    val status: BillingStatus = BillingStatus.LOADING,
    /** Plans with runtime prices from Play (never hardcoded); empty until known. */
    val plans: List<PlanInfo> = emptyList()
) {
    val hasPlans: Boolean get() = plans.isNotEmpty()
}

/** Where the unlocked flag is persisted (SharedPreferences in the app). */
interface PremiumStore {
    fun isPremium(): Boolean
    fun setPremium(premium: Boolean)
}

/** Schedules delayed work; a fake in tests. */
fun interface Scheduler {
    fun postDelayed(delayMs: Long, action: () -> Unit)
}

/**
 * Premium subscription `binaural_premium` (base plans `monthly`, `yearly`, each with a 7-day
 * free-trial offer) via Play Billing.
 *
 * - connects with exponential-backoff retry,
 * - loads plans and prices from Play (no hardcoded price),
 * - restores/re-checks the subscription on every [start]/[refreshPurchases] (app start / resume):
 *   Premium only while Play reports an active subscription, so cancel-then-expiry, refund or
 *   payment failure lock the app again (release builds; debug builds never revoke),
 * - acknowledges purchases, unlocks only on PURCHASED (never on PENDING),
 * - if Play cannot be queried (offline), the last known state is kept.
 *
 * The 7-day trial lives in [com.miferstlab.binauralbeats.data.Entitlements] and is untouched here.
 */
class BillingManager(
    private val gateway: BillingGateway,
    private val store: PremiumStore,
    private val scheduler: Scheduler,
    private val debugBuild: Boolean,
    private val debugUnlockAllowed: () -> Boolean = { false },
    private val onStateChanged: (BillingUiState) -> Unit,
    private val onPremiumChanged: (Boolean) -> Unit
) {
    var uiState: BillingUiState = BillingUiState()
        private set

    private var connected = false
    private var connecting = false
    private var attempt = 0
    private val pendingAcks = mutableSetOf<String>()

    init {
        gateway.updatesListener = { code, purchases -> onPurchasesUpdated(code, purchases) }
    }

    /** Connect (if needed) and restore purchases. Safe to call repeatedly, e.g. from onResume. */
    fun start() {
        if (connected) {
            if (!uiState.hasPlans) loadDetails()
            refreshPurchases()
            return
        }
        if (connecting) return
        connecting = true
        if (uiState.status != BillingStatus.PENDING) {
            update(uiState.copy(status = BillingStatus.LOADING))
        }
        gateway.connect(
            onResult = { ok ->
                connecting = false
                if (ok) {
                    connected = true
                    attempt = 0
                    loadDetails()
                    refreshPurchases()
                } else {
                    connected = false
                    setUnavailable()
                    scheduleReconnect()
                }
            },
            onDisconnected = {
                connected = false
                connecting = false
                scheduleReconnect()
            }
        )
    }

    fun stop() {
        connected = false
        connecting = false
        gateway.updatesListener = null
        gateway.end()
    }

    private fun scheduleReconnect() {
        if (attempt >= MAX_RETRIES) return // next start() (app resume) tries again
        val delay = minOf(BASE_DELAY_MS shl attempt, MAX_DELAY_MS)
        attempt++
        scheduler.postDelayed(delay) { if (!connected) start() }
    }

    private fun loadDetails() {
        gateway.queryPremiumDetails { result ->
            when (result) {
                is DetailsResult.Found -> update(
                    uiState.copy(
                        plans = result.plans,
                        status = if (uiState.status == BillingStatus.PENDING) BillingStatus.PENDING
                        else BillingStatus.READY
                    )
                )
                else -> setUnavailable()
            }
        }
    }

    private fun setUnavailable() {
        if (uiState.status == BillingStatus.PENDING) return
        update(BillingUiState(status = BillingStatus.UNAVAILABLE))
    }

    /** Restore: ask Play what is owned and sync the local flag. */
    fun refreshPurchases() {
        if (!connected) {
            start()
            return
        }
        gateway.queryOwned { result ->
            if (result is OwnedResult.Success) {
                if (result.purchases.isEmpty() && !debugBuild && store.isPremium()) {
                    setPremium(false) // refunded / not owned by this Play account
                }
                handle(result.purchases)
            }
            // On error keep whatever is persisted: offline users keep Premium.
        }
    }

    /**
     * [useTrial]: use the Play free-trial offer when eligible. Pass false while the in-app
     * 7-day trial is still running so nobody gets 14 free days (see Entitlements).
     */
    fun purchasePremium(activity: Activity?, plan: PremiumPlan, useTrial: Boolean) {
        if (debugBuild && debugUnlockAllowed()) {
            setPremium(true)
            return
        }
        if (activity == null) return
        if (!connected || uiState.plans.none { it.plan == plan }) {
            // Details not available: tell the user and try to load again.
            setUnavailable()
            start()
            return
        }
        if (!gateway.launchPurchase(activity, plan, useTrial)) {
            update(uiState.copy(status = BillingStatus.FAILED))
        }
    }

    private fun onPurchasesUpdated(code: GatewayCode, purchases: List<OwnedPurchase>) {
        when (code) {
            GatewayCode.OK -> handle(purchases)
            GatewayCode.ALREADY_OWNED -> refreshPurchases()
            GatewayCode.USER_CANCELED -> restoreReadyIfPossible()
            GatewayCode.UNAVAILABLE -> setUnavailable()
            GatewayCode.ERROR -> update(uiState.copy(status = BillingStatus.FAILED))
        }
    }

    private fun restoreReadyIfPossible() {
        if (uiState.status == BillingStatus.PENDING) return
        update(
            uiState.copy(
                status = if (uiState.hasPlans) BillingStatus.READY else BillingStatus.UNAVAILABLE
            )
        )
    }

    private fun handle(purchases: List<OwnedPurchase>) {
        val purchased = purchases.filter { it.state == OwnedState.PURCHASED }
        if (purchased.isNotEmpty()) {
            setPremium(true)
            if (uiState.status == BillingStatus.PENDING || uiState.status == BillingStatus.FAILED) {
                update(uiState.copy(status = BillingStatus.READY))
            }
            purchased.filter { !it.acknowledged }.forEach { acknowledge(it.purchaseToken) }
        } else if (purchases.any { it.state == OwnedState.PENDING }) {
            // Never unlock on PENDING.
            update(uiState.copy(status = BillingStatus.PENDING))
        } else if (uiState.status == BillingStatus.PENDING) {
            restoreReadyIfPossible()
        }
    }

    private fun acknowledge(token: String, tryNo: Int = 0) {
        if (tryNo == 0 && !pendingAcks.add(token)) return
        gateway.acknowledge(token) { ok ->
            if (ok) {
                pendingAcks.remove(token)
            } else if (tryNo < MAX_RETRIES) {
                scheduler.postDelayed(minOf(BASE_DELAY_MS shl tryNo, MAX_DELAY_MS)) {
                    acknowledge(token, tryNo + 1)
                }
            } else {
                pendingAcks.remove(token) // re-acknowledged on next restore
            }
        }
    }

    private fun setPremium(premium: Boolean) {
        if (store.isPremium() != premium) store.setPremium(premium)
        onPremiumChanged(premium)
    }

    private fun update(state: BillingUiState) {
        if (state == uiState) return
        uiState = state
        onStateChanged(state)
    }

    companion object {
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 60_000L
        const val MAX_RETRIES = 6
    }
}
