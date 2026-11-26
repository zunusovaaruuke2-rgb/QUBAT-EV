@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

/** Presentation only: values come from the same selected monthly finance report. */
@Composable internal fun FinanceProfitSummary(rows: List<NativeMonthFinance>, actual: Boolean, deductions: Set<NativeDeduction>) {
    val profit = knownSum(rows.map { if (actual) it.actualProfitKgs else it.estimatedProfitKgs })
    NativePanel("Прибыль за период", "network-finance-profit") {
        AppText(if (profit == null) "Не хватает данных" else if (actual) "По квитанциям" else "Предварительно",
            fontSize = 13.sp, color = if (profit == null || !actual) Amber else Lime)
        AppText(nativeMoney(profit), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Lime)
        AppText("Доход минус выбранные расходы", fontSize = 14.sp, color = Muted)
        if (!actual && NativeDeduction.ELECTRICITY in deductions)
            AppText("Электричество пока рассчитано по тарифу", fontSize = 12.sp, color = Muted)
        if (actual && profit == null)
            AppText("Для расчёта по квитанциям нужен полный месяц и все необходимые данные", fontSize = 12.sp, color = Muted)
        AppText("Прибыль и выплата — разные суммы", fontSize = 12.sp, color = Muted)
    }
}

@Composable internal fun FinanceBreakdown(rows: List<NativeMonthFinance>, actual: Boolean, deductions: Set<NativeDeduction>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val revenue = knownSum(rows.map { it.revenueKgs })
    val profit = knownSum(rows.map { if (actual) it.actualProfitKgs else it.estimatedProfitKgs })
    val costs = if (revenue != null && profit != null) revenue.subtract(profit) else null
    NativePanel("Из чего складывается", "network-finance-breakdown") {
        FinanceAmount("Доход от зарядок", revenue, "network-finance-income")
        AppText("Оплачено клиентами за вычетом возвратов", fontSize = 12.sp, color = Muted)
        HorizontalDivider()
        FinanceAmount("Расходы", costs, "network-finance-costs")
        PressTextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("network-finance-cost-details")) {
            AppText(if (expanded) "Скрыть расходы" else "Расшифровка расходов", fontSize = 13.sp)
        }
        if (expanded) NativeDeduction.entries.filter { it in deductions }.forEach { deduction ->
            val label = when (deduction) {
                NativeDeduction.ELECTRICITY -> if (actual) "Электричество по квитанциям" else "Электричество по тарифу"
                NativeDeduction.RENT -> if (actual) "Аренда по документам" else "Аренда по тарифу"
                else -> deduction.label
            }
            val value = knownSum(rows.map { month -> when (deduction) {
                NativeDeduction.ELECTRICITY -> if (actual) month.electricityFactKgs else month.electricityEstimateKgs
                NativeDeduction.PLATFORM -> month.platformKgs
                NativeDeduction.ACQUIRING -> month.acquiringKgs
                NativeDeduction.TAX -> month.taxKgs
                NativeDeduction.RENT -> if (actual) month.rentFactKgs else month.rentEstimateKgs
                NativeDeduction.OTHER -> month.otherKgs
                NativeDeduction.PARTNER_SHARE -> month.partnerShareKgs
                NativeDeduction.RESERVE -> month.reserveKgs
                NativeDeduction.PAYOUT -> month.payoutKgs
            } })
            FinanceAmount(label, value, "network-finance-cost-${deduction.name}", small = true)
        }
    }
}

@Composable internal fun FinanceAmount(label: String, amount: BigDecimal?, tag: String, small: Boolean = false) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val labelSize = if (small) 13.sp else 15.sp
        val amountSize = if (small) 15.sp else 17.sp
        val weight = if (small) FontWeight.Normal else FontWeight.SemiBold
        if (maxWidth < 300.dp || fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AppText(label, fontSize = labelSize, color = Muted)
                AppText(nativeMoney(amount), fontSize = amountSize, fontWeight = weight)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText(label, Modifier.weight(1f), fontSize = labelSize, color = Muted)
            AppText(nativeMoney(amount), Modifier.weight(1f), fontSize = amountSize, fontWeight = weight,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable internal fun FinanceSectionButton(title: String, subtitle: String, expanded: Boolean, tag: String, onClick: () -> Unit) {
    val state = LocalTranslate.current(if (expanded) "Развёрнуто" else "Свёрнуто")
    DepthOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag).semantics { stateDescription = state }) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            AppText(subtitle, fontSize = 12.sp, color = Muted)
        }
        AppText(if (expanded) "▴" else "▾")
    }
}
