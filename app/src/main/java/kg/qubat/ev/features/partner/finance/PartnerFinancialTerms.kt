@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeFinancialTerms(repo:NativePartnerRepository,onDismiss:()->Unit,onSaved:()->Unit){
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    val station=stations.first{it.id==stationId}
    val history=repo.tariffs(stationId)
    val base=history.firstOrNull()?:NativeTariff("new",stationId,repo.today)
    var electricity by remember(stationId){mutableStateOf(base.electricityRate.toPlainString())}
    var platform by remember(stationId){mutableStateOf(base.platformRate.movePointRight(2).toPlainString())}
    var acquiring by remember(stationId){mutableStateOf(base.acquiringRate.movePointRight(2).toPlainString())}
    var tax by remember(stationId){mutableStateOf(base.taxRate.movePointRight(2).toPlainString())}
    var rent by remember(stationId){mutableStateOf(base.rentMonthly.toPlainString())}
    var partner by remember(stationId){mutableStateOf(base.partnerRate.movePointRight(2).toPlainString())}
    var fixed by remember(stationId){mutableStateOf(base.partnerFixedMonthly.toPlainString())}
    var reserve by remember(stationId){mutableStateOf(base.reserveRate.movePointRight(2).toPlainString())}
    var date by remember(stationId){mutableStateOf(maxOf(repo.today,base.effectiveFrom.plusDays(1)).toString())}
    var reason by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Условия договора · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){
        AppText("Новая версия действует с выбранной даты. Суммы завершённых зарядок не пересчитываются.",fontSize=11.sp,color=Muted)
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},modifier=Modifier.fillMaxWidth(),label={AppText(s.title)})}
        AppText("Партнёр: ${station.partnerName} · договор ${station.contractId}",fontSize=12.sp,color=Muted)
        NativeField("Закупочная цена, сом/кВт·ч",electricity){electricity=it.take(18)}
        NativeField("Комиссия платформы, %",platform){platform=it.take(8)}
        NativeField("Эквайринг, %",acquiring){acquiring=it.take(8)}
        NativeField("Налог, %",tax){tax=it.take(8)}
        NativeField("Аренда, сом/месяц",rent){rent=it.take(18)}
        NativeField("Доля партнёра, %",partner){partner=it.take(8)}
        NativeField("Или фиксированная доля, сом/месяц",fixed){fixed=it.take(18)}
        NativeField("Резерв, %",reserve){reserve=it.take(8)}
        CalendarDateField("Действует с",date,"network-terms-date"){date=it}
        AppText("Предыдущие версии:",fontWeight=FontWeight.Bold)
        history.forEach{t->AppText("${t.effectiveFrom} — ${t.effectiveTo?:"действует"}: электричество ${nativeMoney(t.electricityRate)}, платформа ${t.platformRate.movePointRight(2)}%, налог ${t.taxRate.movePointRight(2)}%",fontSize=10.sp,color=Muted)}
        NativeField("Причина изменения",reason){reason=it.take(300)}
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{
        fun amount(value:String)=requireNotNull(nativeDecimal(value)?.takeIf{it.signum()>=0}){"Укажите неотрицательную сумму"}
        fun rate(value:String)=requireNotNull(nativeDecimal(value)?.takeIf{it>=BigDecimal.ZERO&&it<=BigDecimal("100")}){"Процент — от 0 до 100"}.movePointLeft(2)
        repo.updateTariff(base.copy(id="terms-${java.util.UUID.randomUUID()}",effectiveFrom=LocalDate.parse(date),effectiveTo=null,electricityRate=amount(electricity),platformRate=rate(platform),acquiringRate=rate(acquiring),taxRate=rate(tax),rentMonthly=amount(rent),partnerRate=rate(partner),partnerFixedMonthly=amount(fixed),reserveRate=rate(reserve)),reason)
        onSaved()
    }.onFailure{error=it.message}},enabled=reason.isNotBlank()){AppText("Сохранить версию")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}
