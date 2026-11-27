package kg.qubat.ev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.maps.android.compose.*

@Composable fun Eyebrow(text:String) {AppText(text,color=Lime,fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)}
@Composable fun Title(text:String) {AppText(text,fontSize=25.sp,fontWeight=FontWeight.Bold)}
@Composable fun Panel(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().depthFrame(RoundedCornerShape(22.dp),SurfaceGreen).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content) }
@Composable fun DemoNotice() {Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B3624)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {AppIcon(Icons.Default.Info,null,tint=Amber,modifier=Modifier.size(18.dp));AppText(" Демо-данные. Станции вымышленные. Платежей нет.",color=Amber,fontSize=11.sp)}}
@Composable fun Metric(label:String,value:String,modifier:Modifier) {Column(modifier.depthFrame(RoundedCornerShape(18.dp),SurfaceGreen).padding(16.dp)) {AppText(label,color=Muted,fontSize=12.sp);Spacer(Modifier.height(8.dp));AppText(value,fontSize=17.sp,fontWeight=FontWeight.Bold)}}
@Composable fun FlowRowCompatStatus(selected:String?,onSelect:(String?)->Unit) {
    Column {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DepthChip(selected=selected==null,onClick={onSelect(null)},label={AppText("Все")})
        DepthChip(selected=selected==StationStatus.AVAILABLE.name,onClick={onSelect(StationStatus.AVAILABLE.name)},label={AppText("Свободны")})
    };Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {StationStatus.entries.filter {it!=StationStatus.AVAILABLE}.forEach {s -> DepthChip(selected=selected==s.name,onClick={onSelect(s.name)},label={AppText(s.title)})}} }
}
