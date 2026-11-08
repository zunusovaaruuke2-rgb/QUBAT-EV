package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ConnectorBadge(charger:Charger,port:Connector,compact:Boolean=false,onClick:()->Unit) {
    val localize=LocalTranslate.current
    val color=Color(port.state.argb)
    val description=remember(charger.number,port,localize) {localize("Аппарат ${charger.number}, разъём ${port.number}, GB/T DC, ${port.state.title}"+(port.percent?.let {", $it процентов, демо"} ?: ""))}
    val status=remember(port.state,localize) {localize(port.state.title)}
    Box(Modifier.fillMaxWidth().height(if(compact)40.dp else 56.dp).testTag("connector-${port.id}")
        .pressClickable(role=Role.Button,onClick=onClick)
        .semantics {contentDescription=description;stateDescription=status},contentAlignment=Alignment.Center) {
        Box(Modifier.size(if(compact)32.dp else 38.dp).depthFrame(CircleShape,SurfaceGreen,0.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {drawCircle(color,style=Stroke(3.dp.toPx()))}
            when(port.state) {
                ConnectorState.CHARGING -> if(port.percent!=null) AppText("${port.percent}%",color=color,fontSize=12.sp,fontWeight=FontWeight.Bold) else AppIcon(Icons.Default.PlayArrow,null,tint=color,modifier=Modifier.size(18.dp))
                ConnectorState.COMPLETED -> AppIcon(Icons.Default.Check,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.ERROR -> AppIcon(Icons.Default.Warning,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.OFFLINE -> AppIcon(Icons.Default.Close,null,tint=color,modifier=Modifier.size(19.dp))
                else -> Canvas(Modifier.size(18.dp)) {
                    drawCircle(color,style=Stroke(1.5.dp.toPx()))
                    val radius=1.8.dp.toPx()
                    drawCircle(color,radius,Offset(size.width*.35f,size.height*.35f))
                    drawCircle(color,radius,Offset(size.width*.65f,size.height*.35f))
                    drawCircle(color,radius,Offset(size.width*.5f,size.height*.64f))
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
        ApparatusDialog(station,unit,onDismiss={openedId=null},onConnector={port->
            openedId=null
            onConnector(port)
        })
    }
    val columns=if(LocalConfiguration.current.screenWidthDp>=348) 3 else 2
    val rows=remember(station.chargers,columns) {station.chargers.chunked(columns)}
    Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            rows.forEach {units->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                    units.forEach {charger->
                        Box(Modifier.weight(1f).depthFrame(RoundedCornerShape(14.dp),MaterialTheme.colorScheme.surfaceVariant,0.dp).pressClickable(role=Role.Button) {openedId=charger.id}.testTag("apparatus-${charger.id}")) {
                            Column(Modifier.padding(vertical=6.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                    AppText(charger.number.toString(),fontSize=13.sp,fontWeight=FontWeight.Bold)
                                    AppText("${charger.powerKw} кВт",color=Lime,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                                    Canvas(Modifier.size(6.dp)) {drawCircle(Color(charger.state.argb))}
                                }
                                Column {charger.connectors.chunked(2).forEach {ports->
                                    Row(Modifier.fillMaxWidth()) {ports.forEach {port->
                                        Box(Modifier.weight(1f)) {ConnectorBadge(charger,port) {onConnector(port)}}
                                    }; if(ports.size==1 && charger.connectors.size>1)Spacer(Modifier.weight(1f))}
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
    val summary=remember(station.availableConnectors,station.connectorCount) {"Свободно ${station.availableConnectors} из ${station.connectorCount} разъёмов · 13,5 сом/кВт·ч"}
    val distance=remember(station.point,location) {location?.let {"${String.format(Ru,"%.1f",distanceKm(it,station.point))} км по прямой"}}
    DepthSurface(onClick=onDetail,shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText(station.title,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    AppText("${station.city} · ДЕМО",color=Muted,fontSize=12.sp)
                }
                PressIconButton(onClick=onDetail) {AppIcon(Icons.Default.Place,"Маршрут и детали ${station.city}",tint=Lime)}
            }
            AppText(summary,color=Muted,fontSize=11.sp)
            distance?.let {AppText(it,color=Muted,fontSize=11.sp)}
            StationEquipment(station,onConnector)
        }
    }
}
