package com.miferstlab.binauralbeats.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.billing.BillingStatus
import com.miferstlab.binauralbeats.billing.PlanInfo
import com.miferstlab.binauralbeats.billing.PremiumPlan
import com.miferstlab.binauralbeats.data.PlaybackState

/**
 * Two choices (yearly / monthly) with runtime prices from Play. The Play free-trial note is shown
 * only after the in-app trial ended (that is also when the trial offer is actually used).
 * If prices are not available, a single generic button keeps the flow (and retry) reachable.
 */
@Composable
fun PremiumPlanButtons(state: PlaybackState, onChoose: (PremiumPlan) -> Unit) {
    val plans = state.premiumPlans
    if (plans.isEmpty()) {
        Button(onClick = { onChoose(PremiumPlan.MONTHLY) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.upgrade_premium))
        }
        return
    }
    val ordered = listOfNotNull(
        plans.firstOrNull { it.plan == PremiumPlan.YEARLY },
        plans.firstOrNull { it.plan == PremiumPlan.MONTHLY }
    )
    Column {
        ordered.forEachIndexed { i, info ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            val label = planLabel(info)
            val note = if (info.trialDays != null && !state.isTrialActive)
                stringResource(R.string.plan_trial_note, info.trialDays, info.formattedPrice)
            else null
            val text = if (note != null) label + "\n" + note else label
            if (info.plan == PremiumPlan.YEARLY) {
                Button(onClick = { onChoose(info.plan) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
            } else {
                OutlinedButton(onClick = { onChoose(info.plan) }, modifier = Modifier.fillMaxWidth()) { Text(text) }
            }
        }
    }
}

@Composable
private fun planLabel(info: PlanInfo): String = when (info.plan) {
    PremiumPlan.MONTHLY -> stringResource(R.string.plan_monthly_label, info.formattedPrice)
    PremiumPlan.YEARLY -> stringResource(R.string.plan_yearly_label, info.formattedPrice)
}

/** Explains why the purchase cannot be started / what is going on; null when nothing to say. */
@Composable
fun billingMessage(state: PlaybackState): String? = when (state.billingStatus) {
    BillingStatus.UNAVAILABLE -> stringResource(R.string.billing_unavailable)
    BillingStatus.PENDING -> stringResource(R.string.billing_pending)
    BillingStatus.FAILED -> stringResource(R.string.billing_failed)
    BillingStatus.LOADING, BillingStatus.READY -> null
}
