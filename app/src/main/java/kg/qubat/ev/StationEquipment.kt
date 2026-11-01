package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ConnectorBadge(charger:Charger,port:Connector,onClick:()->Unit) {
    val localize=LocalTranslate.current
    val color=Color(port.state.argb)
    val description="Аппарат ${charger.number}, разъём ${port.number}, GB/T DC, ${port.state.title}"+(port.percent?.let {", $it процентов, демо"} ?: "")
    Box(Modifier.fillMaxWidth().height(56.dp).testTag("connector-${port.id}")
        .clickable(role=Role.Button,onClick=onClick)
        .semantics {contentDescription=localize(description);stateDescription=localize(port.state.title)},contentAlignment=Alignment.Center) {
        Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {drawCircle(color,style=Stroke(3.dp.toPx()))}
            when(port.state) {
                ConnectorState.CHARGING -> if(port.percent!=null) AppText("${port.percent}%",color=color,fontSize=12.sp,fontWeight=FontWeight.Bold) else AppIcon(Icons.Default.PlayArrow,null,tint=color,modifier=Modifier.size(18.dp))
                ConnectorState.COMPLETED -> AppIcon(Icons.Default.Check,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.ERROR -> AppIcon(Icons.Default.Warning,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.OFFLINE -> AppIcon(Icons.Default.Close,null,tint=color,modifier=Modifier.size(19.dp))
                else -> Canvas(Modifier.size(18.dp)) {
                    drawCircle(color,style=Stroke(1.5.dp.toPx()))
                    listOf(Offset(.35f,.35f),Offset(.65f,.35f),Offset(.5f,.64f)).forEach {p->drawCircle(color,1.8.dp.toPx(),Offset(size.width*p.x,size.height*p.y))}
                }
            }
            Box(Modifier.align(Alignment.TopEnd).offset(x=3.dp,y=(-3).dp).size(19.dp)
                .background(if(LocalNight.current) Color(0xFFECF6FA) else Color(0xFF102638),CircleShape)
                .border(1.5.dp,color,CircleShape),contentAlignment=Alignment.Center) {
                AppText(port.number.toString(),fontSize=11.sp,lineHeight=12.sp,color=if(LocalNight.current) Color(0xFF102638) else Color.White,fontWeight=FontWeight.Black,modifier=Modifier.testTag("port-number-${port.id}"))
            }
        }
    }
}
@Composable fun StationEquipment(station:Station,onConnector:(Connector)->Unit) {
    var openedId by remember(station.id) {mutableStateOf<String?>(null)}
    station.chargers.firstOrNull {it.id==openedId}?.let {unit->
        AlertDialog(onDismissRequest={openedId=null},title={AppText("Аппарат ${unit.number}")},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                AppText("${station.title} · ${unit.powerKw} кВт",fontSize=13.sp)
                unit.connectors.forEach {port->
                    Surface(onClick={openedId=null;onConnector(port)},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.width(56.dp)) {ConnectorBadge(unit,port) {openedId=null;onConnector(port)}}
                            Column {AppText("GB/T · разъём ${port.number}",fontWeight=FontWeight.Bold,fontSize=14.sp);AppText(port.state.title+(port.percent?.let {" · $it%"} ?: ""),fontSize=12.sp,color=Muted)}
                        }
                    }
                }
            }
        },confirmButton={TextButton(onClick={openedId=null}) {AppText("Закрыть")}})
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth>=300.dp) 3 else 2
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            station.chargers.chunked(columns).forEach {units->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                    units.forEach {charger->
                        Surface(onClick={openedId=charger.id},modifier=Modifier.weight(1f).testTag("apparatus-${charger.id}"),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                            Column(Modifier.padding(vertical=6.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                    AppText(charger.number.toString(),fontSize=13.sp,fontWeight=FontWeight.Bold)
                                    AppText("${charger.powerKw} кВт",color=Lime,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                                    Canvas(Modifier.size(6.dp)) {drawCircle(Color(charger.state.argb))}
                                }
                                Row(Modifier.fillMaxWidth()) {charger.connectors.forEach {port->
                                    Box(Modifier.weight(1f)) {ConnectorBadge(charger,port) {onConnector(port)}}
                                }}
                            }
                        }
                    }
                    repeat(columns-units.size) {Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}
@Composable fun LocationEquipmentCard(station:Station,location:GeoPoint?,onDetail:()->Unit,onConnector:(Connector)->Unit) {
    Surface(onClick=onDetail,shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText(station.title,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    AppText("${station.city} · ДЕМО",color=Muted,fontSize=12.sp)
                }
                IconButton(onClick=onDetail) {AppIcon(Icons.Default.Place,"Маршрут и детали ${station.city}",tint=Lime)}
            }
            AppText("Свободно ${station.availableConnectors} из ${station.connectorCount} разъёмов · 13,5 сом/кВт·ч",color=Muted,fontSize=11.sp)
            location?.let {AppText("${String.format(Ru,"%.1f",distanceKm(it,station.point))} км по прямой",color=Muted,fontSize=11.sp)}
            StationEquipment(station,onConnector)
        }
    }
}
