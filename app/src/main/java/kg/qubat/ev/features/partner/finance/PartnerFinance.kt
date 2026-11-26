@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.RoundingMode
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeFinance(repo:NativePartnerRepository,revision:Int,onChanged:()->Unit){
    if(!repo.can(NativePermission.FINANCE)){NativeDenied();return}
    val selected=simpleReportFilters(repo,includePreviousMonth=true)
    var deductions by remember(repo.account.id){mutableStateOf(NativePartnerRepository.defaultDeductions)}
    var formula by remember{mutableStateOf(false)}
    var actual by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var billsOpen by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var payoutsOpen by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var detailsOpen by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var billMonth by remember{mutableStateOf<YearMonth?>(null)}
    var expense by remember{mutableStateOf(false)}
    var payoutEditor by remember{mutableStateOf(false)}
    var terms by remember{mutableStateOf(false)}
    if(selected.filter.stationId!=null && repo.stations(includeArchived=true).none{it.id==selected.filter.stationId}){
        LazyColumn(Modifier.fillMaxSize().testTag("network-finance-scroll"),contentPadding=PaddingValues(18.dp)){
            item{selected.content()}
            item{NativePanel("Точка недоступна"){AppText("Выберите другую точку",color=Muted)}}
        }
        return
    }
    val report=remember(repo,selected.filter,revision,deductions){repo.report(selected.filter,deductions)}
    val rows=report.monthlyFinance
    val context=LocalContext.current
    var attachmentError by remember{mutableStateOf<String?>(null)}
    if(formula)AlertDialog(onDismissRequest={formula=false},title={AppText("Статьи расчёта прибыли")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        AppText("Выручка = оплачено − возвраты. Вычитаются только выбранные статьи. Доля партнёра и её перевод не вычитаются одновременно.",fontSize=12.sp,color=Muted)
        NativeDeduction.entries.forEach{d->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d in deductions,onCheckedChange={checked->deductions=if(checked){var next=deductions+d;if(d==NativeDeduction.PAYOUT)next=next-NativeDeduction.PARTNER_SHARE;if(d==NativeDeduction.PARTNER_SHARE)next=next-NativeDeduction.PAYOUT;next}else deductions-d});AppText(d.label)}}
    }},confirmButton={PressTextButton(onClick={formula=false}){AppText("Применить")}})
    billMonth?.let{month->NativeBillEditor(repo,month,{billMonth=null}){onChanged();billMonth=null}}
    if(expense)NativeExpenseEditor(repo,selected.filter.period.to,{expense=false}){onChanged();expense=false}
    if(payoutEditor)NativeExpenseEditor(repo,selected.filter.period.to,{payoutEditor=false},kindOnly=NativeExpenseKind.PAYOUT){onChanged();payoutEditor=false}
    if(terms)NativeFinancialTerms(repo,{terms=false}){onChanged();terms=false}
    LazyColumn(Modifier.fillMaxSize().testTag("network-finance-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item(key="filters"){selected.content()}
        item(key="profit"){FinanceProfitSummary(rows,actual,deductions)}
        item(key="breakdown"){FinanceBreakdown(rows,actual,deductions)}
        item(key="bills-link"){FinanceSectionButton("Квитанции и расходы","Добавить квитанцию за электричество",billsOpen,"network-finance-bills"){billsOpen=!billsOpen}}
        item(key="payouts-link"){FinanceSectionButton("Выплаты партнёру","Что уже перечислено владельцу",payoutsOpen,"network-finance-payouts"){payoutsOpen=!payoutsOpen}}
        if(payoutsOpen){
            item(key="payout-summary"){NativePanel("Выплаты партнёру"){
                FinanceAmount("Подтверждённые выплаты за период",knownSum(rows.map{it.payoutKgs}),"network-finance-payout-total")
                AppText("Прибыль и выплата — разные суммы",fontSize=12.sp,color=Muted)
            }}
            if(repo.can(NativePermission.EDIT_FINANCE) && report.stations.isNotEmpty())item(key="payout-create"){DepthOutlinedButton(onClick={payoutEditor=true},modifier=Modifier.fillMaxWidth().testTag("network-payout-create")){AppText("Записать выплату")}}
            val payouts=report.expenses.filter{it.kind==NativeExpenseKind.PAYOUT}
            if(payouts.isEmpty())item(key="payout-empty"){AppText("Записей о выплатах за период нет",fontSize=13.sp,color=Muted)}
            items(payouts,key={"payout-${it.id}"},contentType={"payout"}){payment->NativePanel(report.stations.firstOrNull{it.station.id==payment.stationId}?.station?.title?:payment.stationId){
                AppText(nativeMoney(payment.amountKgs),fontWeight=FontWeight.SemiBold,color=Lime)
                AppText(nativeDate.format(payment.date),fontSize=13.sp)
                AppText(if(payment.paid)"Подтверждена · демо" else "Не подтверждена · демо",fontSize=13.sp,color=Muted)
                if(payment.note.isNotBlank())AppText(payment.note,fontSize=13.sp)
            }}
        }
        item(key="export"){NativeExport(repo,report,onChanged)}
        item(key="details-link"){FinanceSectionButton("Подробный расчёт","Сверка, статьи расходов и условия договора",detailsOpen,"network-finance-details"){detailsOpen=!detailsOpen}}
        if(billsOpen){
            item(key="mode"){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DepthChip(!actual,{actual=false},label={AppText("По тарифам")});DepthChip(actual,{actual=true},Modifier.testTag("network-finance-actual"),label={AppText("По квитанциям")})}}
            item(key="rule"){AppText("Квитанция учитывается только за полный месяц. Неизвестные суммы показываются как «Нет данных».",fontSize=12.sp,color=Muted)}
        }
        if(detailsOpen)item(key="reconcile"){NativePanel("Акт сверки"){
            AppText("Начислено: ${nativeMoney(report.summary.chargedKgs)}")
            AppText("Оплачено по сессиям: ${nativeMoney(report.summary.paidKgs)}")
            AppText("Поступления реестра: ${nativeMoney(report.summary.paymentLedgerKgs)}")
            AppText("Возвраты: ${nativeMoney(report.summary.refundKgs)}")
            AppText("Разница: ${nativeMoney(report.summary.reconciliationDifferenceKgs)}",fontWeight=FontWeight.Bold,color=Lime)
            AppText("Не оплачено: ${nativeMoney(report.summary.unpaidKgs)}",fontSize=12.sp,color=Muted)
            PressTextButton(onClick={formula=true},modifier=Modifier.testTag("network-finance-formula")){AppText("Статьи расчёта")}
        }}
        if(billsOpen && repo.can(NativePermission.EDIT_FINANCE) && report.stations.isNotEmpty())item(key="expense"){DepthOutlinedButton(onClick={expense=true},modifier=Modifier.fillMaxWidth().testTag("network-expense-create")){AppText("Добавить расход")}}
        if(detailsOpen && repo.can(NativePermission.EDIT_TARIFF))item(key="terms"){DepthOutlinedButton(onClick={terms=true},modifier=Modifier.fillMaxWidth().testTag("network-financial-terms")){AppText("Договорные ставки и история")}}
        attachmentError?.let{message->item(key="attachment-error"){AppText(message,color=MaterialTheme.colorScheme.error)}}
        if(billsOpen)items(report.monthBills,key={"invoice-${it.id}"},contentType={"invoice"}){invoice->NativePanel("Квитанция №${invoice.billNumber}","network-invoice-${invoice.id}"){
            AppText(report.stations.firstOrNull{it.station.id==invoice.stationId}?.station?.title?:invoice.stationId)
            AppText("${invoice.month} · ${nativeMoney(invoice.electricityKgs)}",color=Lime,fontWeight=FontWeight.Bold)
            AppText("Дата документа: ${invoice.billDate} · ${if(invoice.paid)"Оплачено · ДЕМО" else "Не оплачено"}",fontSize=12.sp)
            AppText("Обновлено: ${nativeTime.format(invoice.updatedAt)} · ${invoice.authorId}",fontSize=10.sp,color=Muted)
            AppText(invoice.comment,fontSize=12.sp,color=Muted)
            invoice.attachment?.let{file->
                AppText("Вложение: ${file.fileName} · ${file.byteSize} байт",fontSize=11.sp)
                if(file.localReference.isNotBlank())PressTextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(file.localReference),file.mediaType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{attachmentError="Не удалось открыть вложение. Выберите файл заново."}}){AppText("Открыть квитанцию")}
            }
        }}
        if(rows.isEmpty())item(key="empty"){NativePanel("За период финансовых записей не найдено"){AppText("Измените даты или выбранную станцию",color=Muted)}}
        if(billsOpen || detailsOpen)items(rows,key={it.month.toString()},contentType={"month"}){m->NativePanel("${m.month} · ${m.stationIds.size} точек","network-month-${m.month}"){
            AppText(nativeEnergy(m.energyKwh),color=Lime)
            AppText("Оплаченная выручка: ${nativeMoney(m.revenueKgs)}")
            AppText("Электричество: расчёт ${nativeMoney(m.electricityEstimateKgs)}\nФакт ${nativeMoney(m.electricityFactKgs)}",Modifier.testTag("network-month-electricity"),fontSize=12.sp)
            AppText("Показания счётчика: ${m.meterEnergyKwh?.let(::nativeEnergy)?:"Нет данных"}\nРазница счётчика и сессий: ${m.meterDifferenceKwh?.let(::nativeEnergy)?:"Нет данных"}",fontSize=11.sp,color=Muted)
            AppText("Платформа: ${nativeMoney(m.platformKgs)} · эквайринг: ${nativeMoney(m.acquiringKgs)}\nНалог: ${nativeMoney(m.taxKgs)}",fontSize=12.sp)
            AppText("Аренда: расчёт ${nativeMoney(m.rentEstimateKgs)} · факт ${nativeMoney(m.rentFactKgs)}\nПрочее: ${nativeMoney(m.otherKgs)}",fontSize=12.sp)
            AppText("Доля партнёра: ${nativeMoney(m.partnerShareKgs)}\nРезерв: ${nativeMoney(m.reserveKgs)} · перевод: ${nativeMoney(m.payoutKgs)}",fontSize=12.sp)
            AppText("Прибыль: ${nativeMoney(if(actual)m.actualProfitKgs else m.estimatedProfitKgs)}",fontWeight=FontWeight.Bold,color=Lime)
            val margin=if(actual)m.actualMarginPercent else m.estimatedMarginPercent
            AppText("Маржа: ${margin?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.plus(" %")?:"Нет данных"}",fontSize=12.sp)
            AppText("Задолженность с переносом: ${nativeMoney(m.outstandingKgs)}",fontSize=11.sp,color=Muted)
            AppText("Квитанций: ${m.billCount}, оплачено: ${m.billPaidCount}",fontSize=11.sp,color=Muted)
            m.factUnavailableReason?.let{AppText(it,fontSize=11.sp,color=Muted)}
            if(repo.can(NativePermission.EDIT_FINANCE))PressTextButton(onClick={billMonth=m.month},modifier=Modifier.testTag("network-bill-edit-${m.month}")){AppText("Квитанция и аренда за месяц")}
        }}
    }
}
