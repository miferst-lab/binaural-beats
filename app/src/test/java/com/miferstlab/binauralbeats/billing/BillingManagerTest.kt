package com.miferstlab.binauralbeats.billing

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeGateway : BillingGateway {
    override var updatesListener: ((GatewayCode, List<OwnedPurchase>) -> Unit)? = null
    var connectResults = ArrayDeque<Boolean>()
    var details: DetailsResult = DetailsResult.Found(
        listOf(
            PlanInfo(PremiumPlan.MONTHLY, "14,99 zł", 7),
            PlanInfo(PremiumPlan.YEARLY, "99,99 zł", 7)
        )
    )
    var lastPlan: PremiumPlan? = null
    var lastUseTrial: Boolean? = null
    var owned: OwnedResult = OwnedResult.Success(emptyList())
    var ackResults = ArrayDeque<Boolean>()
    var launchOk = true
    var connects = 0
    var launches = 0
    val acked = mutableListOf<String>()
    var onDisc: (() -> Unit)? = null

    override fun connect(onResult: (Boolean) -> Unit, onDisconnected: () -> Unit) {
        connects++
        onDisc = onDisconnected
        onResult(connectResults.removeFirstOrNull() ?: true)
    }
    override fun queryPremiumDetails(onResult: (DetailsResult) -> Unit) = onResult(details)
    override fun queryOwned(onResult: (OwnedResult) -> Unit) = onResult(owned)
    override fun launchPurchase(activity: Activity, plan: PremiumPlan, useTrial: Boolean): Boolean {
        launches++; lastPlan = plan; lastUseTrial = useTrial; return launchOk
    }
    override fun acknowledge(purchaseToken: String, onResult: (Boolean) -> Unit) {
        acked += purchaseToken
        onResult(ackResults.removeFirstOrNull() ?: true)
    }
    override fun end() {}
}

private class FakeStore(@JvmField var premium: Boolean = false) : PremiumStore {
    override fun isPremium() = premium
    override fun setPremium(premium: Boolean) { this.premium = premium }
}

class BillingManagerTest {
    private lateinit var gw: FakeGateway
    private lateinit var store: FakeStore
    private val delays = mutableListOf<Long>()
    private val queued = ArrayDeque<() -> Unit>()
    private var debugFlag = false
    private val activity: Activity? get() = null

    private fun manager(debug: Boolean = false) = BillingManager(
        gateway = gw, store = store,
        scheduler = { d, a -> delays += d; queued += a },
        debugBuild = debug,
        debugUnlockAllowed = { debugFlag },
        onStateChanged = {},
        onPremiumChanged = {}
    )

    private fun runQueued() { while (queued.isNotEmpty()) queued.removeFirst()() }

    @Before fun setUp() { gw = FakeGateway(); store = FakeStore(); debugFlag = false }

    @Test fun startLoadsPriceFromPlay() {
        val m = manager(); m.start()
        assertEquals(BillingStatus.READY, m.uiState.status)
        assertEquals(listOf("14,99 zł", "99,99 zł"), m.uiState.plans.map { it.formattedPrice })
        assertFalse(store.premium)
    }

    @Test fun connectRetriesWithBackoff() {
        gw.connectResults.addAll(listOf(false, false, true))
        val m = manager(); m.start()
        assertEquals(BillingStatus.UNAVAILABLE, m.uiState.status)
        runQueued()
        assertEquals(3, gw.connects)
        assertEquals(listOf(1_000L, 2_000L), delays)
        assertEquals(BillingStatus.READY, m.uiState.status)
    }

    @Test fun retriesAreBounded() {
        repeat(20) { gw.connectResults.add(false) }
        val m = manager(); m.start(); runQueued()
        assertEquals(BillingManager.MAX_RETRIES + 1, gw.connects)
        assertTrue(delays.max() <= BillingManager.MAX_DELAY_MS)
    }

    @Test fun productNotFoundIsUnavailableWithoutPrice() {
        gw.details = DetailsResult.NotFound
        val m = manager(); m.start()
        assertEquals(BillingStatus.UNAVAILABLE, m.uiState.status)
        assertTrue(m.uiState.plans.isEmpty())
        m.purchasePremium(fakeActivity(), PremiumPlan.MONTHLY, true)
        assertEquals(0, gw.launches)
    }

    @Test fun restoreOnStartUnlocksAndAcknowledges() {
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t1", OwnedState.PURCHASED, false)))
        manager().start()
        assertTrue(store.premium)
        assertEquals(listOf("t1"), gw.acked)
    }

    @Test fun alreadyAcknowledgedIsNotAcknowledgedAgain() {
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t1", OwnedState.PURCHASED, true)))
        manager().start()
        assertTrue(store.premium)
        assertTrue(gw.acked.isEmpty())
    }

    @Test fun pendingPurchaseDoesNotUnlock() {
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t1", OwnedState.PENDING, false)))
        val m = manager(); m.start()
        assertFalse(store.premium)
        assertEquals(BillingStatus.PENDING, m.uiState.status)
        assertTrue(gw.acked.isEmpty())
    }

    @Test fun pendingThenPurchasedUnlocks() {
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t1", OwnedState.PENDING, false)))
        val m = manager(); m.start()
        gw.updatesListener!!(GatewayCode.OK, listOf(OwnedPurchase("t1", OwnedState.PURCHASED, false)))
        assertTrue(store.premium)
        assertEquals(BillingStatus.READY, m.uiState.status)
        assertEquals(listOf("t1"), gw.acked)
    }

    @Test fun purchaseUpdateUnlocks() {
        val m = manager(); m.start()
        gw.updatesListener!!(GatewayCode.OK, listOf(OwnedPurchase("t2", OwnedState.PURCHASED, false)))
        assertTrue(store.premium)
    }

    @Test fun userCancelKeepsLockedAndReady() {
        val m = manager(); m.start()
        gw.updatesListener!!(GatewayCode.USER_CANCELED, emptyList())
        assertFalse(store.premium)
        assertEquals(BillingStatus.READY, m.uiState.status)
    }

    @Test fun errorShowsFailed() {
        val m = manager(); m.start()
        gw.updatesListener!!(GatewayCode.ERROR, emptyList())
        assertEquals(BillingStatus.FAILED, m.uiState.status)
        assertFalse(store.premium)
    }

    @Test fun acknowledgeFailureIsRetried() {
        gw.ackResults.addAll(listOf(false, true))
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t1", OwnedState.PURCHASED, false)))
        manager().start(); runQueued()
        assertEquals(listOf("t1", "t1"), gw.acked)
    }

    @Test fun launchPassesChosenPlanAndTrialFlag() {
        val m = manager(); m.start()
        m.purchasePremium(fakeActivity(), PremiumPlan.YEARLY, useTrial = true)
        assertEquals(PremiumPlan.YEARLY, gw.lastPlan)
        assertEquals(true, gw.lastUseTrial)
        m.purchasePremium(fakeActivity(), PremiumPlan.MONTHLY, useTrial = false)
        assertEquals(PremiumPlan.MONTHLY, gw.lastPlan)
        assertEquals(false, gw.lastUseTrial)
    }

    @Test fun missingPlanDoesNotLaunch() {
        gw.details = DetailsResult.Found(listOf(PlanInfo(PremiumPlan.MONTHLY, "14,99 zł", null)))
        val m = manager(); m.start()
        m.purchasePremium(fakeActivity(), PremiumPlan.YEARLY, true)
        assertEquals(0, gw.launches)
    }

    @Test fun expiredSubscriptionRevokesPremiumInRelease() {
        store.premium = true
        gw.owned = OwnedResult.Success(emptyList())
        val m = manager(); m.start()
        assertFalse(store.premium)
        // renewed later
        gw.owned = OwnedResult.Success(listOf(OwnedPurchase("t9", OwnedState.PURCHASED, true)))
        m.refreshPurchases()
        assertTrue(store.premium)
    }

    @Test fun releaseRevokesWhenNotOwned() {
        store.premium = true
        manager(debug = false).start()
        assertFalse(store.premium)
    }

    @Test fun releaseKeepsPremiumWhenPlayQueryFails() {
        store.premium = true
        gw.owned = OwnedResult.Error
        manager(debug = false).start()
        assertTrue(store.premium)
    }

    @Test fun debugBuildKeepsLocalUnlock() {
        store.premium = true
        manager(debug = true).start()
        assertTrue(store.premium)
    }

    @Test fun debugUnlockOnlyHonoredInDebugBuilds() {
        debugFlag = true
        val rel = manager(debug = false); rel.start()
        rel.purchasePremium(fakeActivity(), PremiumPlan.MONTHLY, true)
        assertFalse(store.premium)
        assertEquals(1, gw.launches)
        val dbg = manager(debug = true); dbg.start()
        dbg.purchasePremium(null, PremiumPlan.MONTHLY, true)
        assertTrue(store.premium)
    }
}

/** Activity is only passed through to the fake gateway; allocate without running its constructor chain. */
private fun fakeActivity(): Activity {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val f = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
    val u = f.get(null)
    val m = unsafeClass.getMethod("allocateInstance", Class::class.java)
    return m.invoke(u, Activity::class.java) as Activity
}
