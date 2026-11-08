@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.*

internal data class NativeFilterUi(val filter:NativeReportFilter,val content:@Composable ()->Unit)
@Composable internal fun nativeFilters(repo:NativePartnerRepository):NativeFilterUi {
    var from by rememberSaveable(repo.account.id){mutableStateOf(repo.today.minusDays(6).toString())}
    var to by rememberSaveable(repo.account.id){mutableStateOf(repo.today.toString())}
    var stationId by rememberSaveable{mutableStateOf("")}
    var organizationId by rememberSaveable{mutableStateOf("")}
    var ownerId by rememberSaveable{mutableStateOf("")}
    var groupId by rememberSaveable{mutableStateOf("")}
    var locationId by rememberSaveable{mutableStateOf("")}
    var portId by rememberSaveable{mutableStateOf("")}
    var customerId by rememberSaveable{mutableStateOf("")}
    var query by rememberSaveable{mutableStateOf("")}
    var payment by rememberSaveable{mutableStateOf("")}
    var status by rememberSaveable{mutableStateOf("")}
    var operation by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableStateOf(PartnerSort.NEWEST.name)}
    var edit by remember{mutableStateOf(false)}
    var picker by remember{mutableStateOf<String?>(null)}
    val stations=repo.stations(includeArchived=true)
    val filter=NativeReportFilter(PartnerPeriod(LocalDate.parse(from),LocalDate.parse(to)),organizationId.takeIf{it.isNotBlank()},ownerId.takeIf{it.isNotBlank()},groupId.takeIf{it.isNotBlank()},locationId.takeIf{it.isNotBlank()},stationId.takeIf{it.isNotBlank()},portId.takeIf{it.isNotBlank()},customerId.takeIf{it.isNotBlank()},operation.takeIf{it.isNotBlank()}?.let(NativeOperation::valueOf),payment.takeIf{it.isNotBlank()}?.let(NativePaymentStatus::valueOf),status.takeIf{it.isNotBlank()}?.let(NativeSessionStatus::valueOf),query,PartnerSort.valueOf(sort))
    if(edit)NativeDateRange(from,to,{edit=false}){a,b->from=a;to=b;edit=false}
    picker?.let{kind->
        val choices=when(kind){
            "station"->stations.map{it.id to it.title}
            "organization"->repo.organizations().map{it.id to it.name}
            "owner"->repo.organizations().map{it.ownerId to (NativePartnerRepository.accounts.firstOrNull{a->a.id==it.ownerId}?.name?:it.ownerId)}
            "group"->stations.map{it.groupId to it.groupId}.distinct()
            "location"->stations.map{it.locationId to "${it.city} · ${it.locationId}"}.distinct()
            "port"->stations.flatMap{s->s.ports.map{it.id to "${s.title} · №${it.number}"}}
            "payment"->NativePaymentStatus.entries.map{it.name to it.label}
            "status"->NativeSessionStatus.entries.map{it.name to it.label}
            "operation"->NativeOperation.entries.map{it.name to it.label}
            else->emptyList()
        }
        AlertDialog(onDismissRequest={picker=null},title={AppText("Фильтр")},text={Column(Modifier.verticalScroll(rememberScrollState())){(listOf("" to "Все")+choices).forEach{(id,label)->PressTextButton(onClick={when(kind){"station"->stationId=id;"organization"->organizationId=id;"owner"->ownerId=id;"group"->groupId=id;"location"->locationId=id;"port"->portId=id;"payment"->payment=id;"status"->status=id;"operation"->operation=id};picker=null},modifier=Modifier.testTag("network-select-$kind-$id")){AppText(label)}}}},confirmButton={PressTextButton(onClick={picker=null}){AppText("Закрыть")}})
    }
    val content:@Composable ()->Unit={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                listOf("Сегодня" to "today","7 дней" to "week","Месяц" to "month","Год" to "year","Прошлый месяц" to "previous").forEach{(label,id)->DepthOutlinedButton(onClick={val end=if(id=="previous")repo.today.withDayOfMonth(1).minusDays(1) else repo.today;to=end.toString();from=when(id){"today"->end;"week"->end.minusDays(6);"year"->end.withDayOfYear(1);else->end.withDayOfMonth(1)}.toString()},modifier=Modifier.testTag("network-period-$id")){AppText(label)}}
            }
            AppText("${nativeDate.format(filter.period.from)} — ${nativeDate.format(filter.period.to)} · Asia/Bishkek",fontSize=12.sp,color=Muted,modifier=Modifier.testTag("network-period-label").pressClickable{edit=true})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                DepthOutlinedButton(onClick={picker="station"},modifier=Modifier.weight(1f).testTag("network-station-filter")){AppText(stations.firstOrNull{it.id==stationId}?.title?:"Все мои станции",maxLines=1)}
                PressTextButton(onClick={edit=true},modifier=Modifier.testTag("network-period-custom")){AppText("Период")}
            }
            PressOutlinedTextField(query,{query=it.take(100)},modifier=Modifier.fillMaxWidth().testTag("network-search"),label={AppText("Поиск по станции / ID")},singleLine=true)
            var advanced by rememberSaveable{mutableStateOf(false)}
            PressTextButton(onClick={advanced=!advanced}){AppText(if(advanced)"Скрыть фильтры" else "Дополнительные фильтры")}
            if(advanced){
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("organization" to "Организация","owner" to "Владелец","group" to "Группа","location" to "Локация","port" to "Разъём","payment" to "Оплата","status" to "Сессия","operation" to "Операция").forEach{(id,label)->DepthOutlinedButton(onClick={picker=id}){AppText(label)}}}
                PressOutlinedTextField(customerId,{customerId=it.take(80)},label={AppText("ID демо-клиента")},singleLine=true,modifier=Modifier.fillMaxWidth())
                PartnerSort.entries.forEach{s->DepthChip(sort==s.name,{sort=s.name},label={AppText(s.label)})}
                PressTextButton(onClick={stationId="";organizationId="";ownerId="";groupId="";locationId="";portId="";customerId="";payment="";status="";operation="";query=""}){AppText("Сбросить фильтры")}
            }
        }
    }
    return NativeFilterUi(filter,content)
}

@Composable private fun NativeDateRange(from:String,to:String,onDismiss:()->Unit,onApply:(String,String)->Unit){
    var first by remember{mutableStateOf(from)};var last by remember{mutableStateOf(to)}
    val valid=runCatching{PartnerPeriod(LocalDate.parse(first),LocalDate.parse(last))}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Период отчёта")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        AppText("Последний день включается целиком по Asia/Bishkek.",fontSize=12.sp,color=Muted)
        CalendarDateField("С даты",first,"network-date-from"){first=it}
        CalendarDateField("По дату",last,"network-date-to"){last=it}
        if(!valid)AppText("Проверьте начало и окончание периода",color=MaterialTheme.colorScheme.error)
    }},confirmButton={PressTextButton(onClick={onApply(first,last)},enabled=valid,modifier=Modifier.testTag("network-date-apply")){AppText("Применить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}
