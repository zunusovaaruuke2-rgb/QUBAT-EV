@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

internal val nativeTime=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(PARTNER_ZONE)
internal val nativeDate=DateTimeFormatter.ofPattern("dd.MM.yyyy")
internal fun nativeMoney(value:BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" сом") ?: "Нет данных"
internal fun nativeEnergy(value:BigDecimal)=value.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',')+" кВт·ч"
internal fun knownSum(values:List<BigDecimal?>):BigDecimal?=if(values.isEmpty()||values.any{it==null})null else values.filterNotNull().fold(BigDecimal.ZERO,BigDecimal::add)

@Composable internal fun NativePanel(title:String,tag:String="",content:@Composable ColumnScope.()->Unit) {
    DepthSurface(Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {AppText(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold);content()}
    }
}
@Composable internal fun NativeMetric(title:String,value:String,tag:String="") {NativePanel(title,tag){AppText(value,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Lime)}}
@Composable internal fun NativeNotice(){AppText("ДЕМО. Все станции, показания и суммы вымышленные. Реальные платежи и управление оборудованием отключены.",fontSize=11.sp,color=Muted)}

@Composable internal fun NativeField(label:String,value:String,tag:String="",onChange:(String)->Unit){
    PressOutlinedTextField(value,onChange,label={AppText(label)},singleLine=true,modifier=Modifier.fillMaxWidth().testTag(tag).depthFrame(RoundedCornerShape(16.dp),SurfaceGreen,1.dp,fill=false))
}
internal fun nativeDecimal(value:String)=value.trim().replace(',','.').toBigDecimalOrNull()

@Composable internal fun NativeDenied(){Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){AppText("Нет права просмотра этого раздела",fontSize=22.sp,fontWeight=FontWeight.Bold);AppText("Роли и назначения проверяются в демонстрационном репозитории. Реальная серверная авторизация появится после подключения backend.",color=Muted)}}
