@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeBillEditor(repo:NativePartnerRepository,month:YearMonth,onDismiss:()->Unit,onSaved:()->Unit){
    val context=LocalContext.current
    val stations=repo.stations(includeArchived=true)
    var stationId by remember{mutableStateOf(stations.first().id)}
    val existing=repo.monthBills(stationId).firstOrNull{it.month==month}
    var amount by remember(stationId){mutableStateOf(existing?.electricityKgs?.toPlainString().orEmpty())}
    var number by remember(stationId){mutableStateOf(existing?.billNumber.orEmpty())}
    var date by remember(stationId){mutableStateOf(existing?.billDate?.toString()?:month.atEndOfMonth().toString())}
    var meter by remember(stationId){mutableStateOf(existing?.meterEnergyKwh?.toPlainString().orEmpty())}
    var rent by remember(stationId){mutableStateOf(existing?.actualRentKgs?.toPlainString().orEmpty())}
    var paid by remember(stationId){mutableStateOf(existing?.paid?:false)}
    var comment by remember(stationId){mutableStateOf(existing?.comment.orEmpty())}
    var reason by remember{mutableStateOf("")}
    var attachment by remember(stationId){mutableStateOf(existing?.attachment)}
    var error by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)runCatching{
            var fileName="Квитанция";var size=0L
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{cursor->if(cursor.moveToFirst()){fileName=cursor.getString(0).orEmpty();size=cursor.getLong(1)}}
            require(size in 0..10*1024*1024){"Вложение — до 10 МБ"}
            runCatching{context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            attachment=NativeAttachment(fileName,context.contentResolver.getType(uri)?:"application/octet-stream",size,uri.toString())
        }.onFailure{error=it.message}
    }
    val valid=nativeDecimal(amount)?.signum()?.let{it>=0}==true&&number.isNotBlank()&&reason.isNotBlank()&&runCatching{LocalDate.parse(date)}.isSuccess&&(meter.isBlank()||nativeDecimal(meter)?.signum()?.let{it>=0}==true)&&(rent.isBlank()||nativeDecimal(rent)?.signum()?.let{it>=0}==true)
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Квитанция · $month · ДЕМО")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(11.dp)){
        AppText("Расчётная сумма не удаляется. Данные сохраняются только на этом устройстве.",fontSize=11.sp,color=Muted)
        stations.forEach{s->DepthChip(stationId==s.id,{stationId=s.id},modifier=Modifier.fillMaxWidth(),label={AppText(s.title)})}
        NativeField("Электричество по квитанции, сом",amount,"network-bill-amount"){amount=it.take(18)}
        NativeField("Номер документа",number,"network-bill-number"){number=it.take(80)}
        CalendarDateField("Дата документа",date,"network-bill-date"){date=it}
        NativeField("Внешний счётчик, кВт·ч · необязательно",meter){meter=it.take(18)}
        NativeField("Фактическая аренда, сом · необязательно",rent){rent=it.take(18)}
        DepthChip(paid,{paid=!paid},label={AppText("Квитанция оплачена · ДЕМО")})
        DepthOutlinedButton(onClick={picker.launch(arrayOf("application/pdf","image/*"))}){AppText("Прикрепить квитанцию")}
        attachment?.let{AppText("${it.fileName} · ${it.byteSize} байт",fontSize=11.sp,color=Muted)}
        NativeField("Комментарий",comment){comment=it.take(300)}
        NativeField("Причина изменения",reason,"network-bill-reason"){reason=it.take(300)}
        error?.let{AppText(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={PressTextButton(onClick={runCatching{repo.saveMonthBill(NativeMonthBill(existing?.id?:"bill-${stationId}-${month}",stationId,month,nativeDecimal(amount)!!,number,LocalDate.parse(date),paid,nativeDecimal(meter),nativeDecimal(rent),comment,attachment),reason);onSaved()}.onFailure{error=it.message}},enabled=valid,modifier=Modifier.testTag("network-bill-save")){AppText("Сохранить")}},dismissButton={PressTextButton(onClick=onDismiss){AppText("Отмена")}})
}
