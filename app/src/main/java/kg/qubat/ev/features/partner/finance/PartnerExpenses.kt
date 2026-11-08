@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeExpenseEditor(repo:NativePartnerRepository,initialDate:LocalDate,onDismiss:()->Unit,onSaved:()->Unit){
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    var kind by remember{mutableStateOf(NativeExpenseKind.OTHER)}
    var amount by remember{mutableStateOf("")};var date by remember{mutableStateOf(initialDate.toString())};var reason by remember{mutableStateOf("")};var paid by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val valid=nativeDecimal(amount)?.signum()?.let{it>=0}==true&&reason.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Расход / выплата · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},label={AppText(s.title)})}
        NativeExpenseKind.entries.forEach{k->DepthChip(kind==k,{kind=k},label={AppText(k.label)})}
        NativeField("Сумма, сом",amount,"network-expense-amount"){amount=it.take(18)}
        CalendarDateField("Дата",date,"network-expense-date"){date=it}
        NativeField("Причина / документ",reason,"network-expense-reason"){reason=it.take(300)}
        DepthChip(paid,{paid=!paid},label={AppText("Оплачено / подтверждено · ДЕМО")})
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{repo.saveExpense(NativeExpense("expense-${java.util.UUID.randomUUID()}",stationId,LocalDate.parse(date),kind,nativeDecimal(amount)!!,reason,paid=paid),reason);onSaved()}.onFailure{error=it.message}},enabled=valid,modifier=Modifier.testTag("network-expense-save")){AppText("Сохранить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}
