package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ReportEnergySummary(summary: NativeSummary) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("network-report-summary")) {
        if (maxWidth < 300.dp || fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportStat("Зарядок завершено", summary.completedCount.toString(), "network-report-completed")
                ReportStat("Отпущено, кВт·ч", nativeEnergy(summary.energyKwh).removeSuffix(" кВт·ч"), "network-report-energy")
            }
        } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()) { ReportStat("Зарядок завершено", summary.completedCount.toString(), "network-report-completed", Modifier.fillMaxHeight()) }
            Box(Modifier.weight(1f).fillMaxHeight()) { ReportStat("Отпущено, кВт·ч", nativeEnergy(summary.energyKwh).removeSuffix(" кВт·ч"), "network-report-energy", Modifier.fillMaxHeight()) }
        }
    }
}

@Composable private fun ReportStat(label: String, value: String, tag: String, modifier: Modifier = Modifier) {
    DepthSurface(modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Lime)
            AppText(label, fontSize = 14.sp, color = Muted)
        }
    }
}

@Composable internal fun ReportMoneySummary(summary: NativeSummary) {
    NativePanel("Деньги за период", "network-report-money") {
        ReportMoneyRow("Начислено за зарядки", nativeMoney(summary.chargedKgs), "network-report-charged")
        HorizontalDivider()
        ReportMoneyRow("Оплачено клиентами", nativeMoney(summary.paidKgs), "network-report-paid", paid = true)
        HorizontalDivider()
        ReportMoneyRow("Возвраты клиентам", nativeMoney(summary.refundKgs), "network-report-refunds")
        HorizontalDivider()
        ReportMoneyRow("Осталось оплатить", nativeMoney(summary.unpaidKgs), "network-report-unpaid")
        if (summary.unknownPaymentCount > 0) AppText("Часть данных об оплате отсутствует", fontSize = 12.sp, color = Muted)
    }
}

@Composable private fun ReportMoneyRow(label: String, value: String, tag: String, paid: Boolean = false) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val color = if (paid) Lime else MaterialTheme.colorScheme.onSurface
        if (maxWidth < 340.dp || fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(label, fontSize = 14.sp, color = Muted)
                AppText(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = color)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText(label, Modifier.weight(1f), fontSize = 14.sp, color = Muted)
            AppText(value, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = color,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
