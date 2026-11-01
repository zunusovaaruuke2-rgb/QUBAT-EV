package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable fun ConnectorLegend() {
    val items=listOf(ConnectorState.FREE to "Свободен",ConnectorState.CHARGING to "Заряжается",ConnectorState.CONNECTED to "Подключён",ConnectorState.COMPLETED to "Завершён / ошибка",ConnectorState.OFFLINE to "Недоступен")
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        items.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {row.forEach {(state,title)->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Canvas(Modifier.size(7.dp)) {drawCircle(Color(state.argb))}
                Text(title,color=Muted,fontSize=10.sp)
            }
        }}}
    }
}
@Composable fun ConnectorBadge(charger:Charger,port:Connector,onClick:()->Unit) {
    val color=Color(port.state.argb)
    val description="Аппарат ${charger.number}, разъём ${port.number}, GB/T DC, ${port.state.title}"+(port.percent?.let {", $it процентов, демо"} ?: "")
    Box(Modifier.fillMaxWidth().height(56.dp).testTag("connector-${port.id}")
        .clickable(role=Role.Button,onClick=onClick)
        .semantics {contentDescription=description;stateDescription=port.state.title},contentAlignment=Alignment.Center) {
        Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {drawCircle(color,style=Stroke(3.dp.toPx()))}
            when(port.state) {
                ConnectorState.CHARGING -> if(port.percent!=null) Text("${port.percent}%",color=color,fontSize=12.sp,fontWeight=FontWeight.Bold) else Icon(Icons.Default.PlayArrow,null,tint=color,modifier=Modifier.size(18.dp))
                ConnectorState.COMPLETED -> Icon(Icons.Default.Check,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.ERROR -> Icon(Icons.Default.Warning,null,tint=color,modifier=Modifier.size(19.dp))
                ConnectorState.OFFLINE -> Icon(Icons.Default.Close,null,tint=color,modifier=Modifier.size(19.dp))
                else -> Canvas(Modifier.size(18.dp)) {
                    drawCircle(color,style=Stroke(1.5.dp.toPx()))
                    listOf(Offset(.35f,.35f),Offset(.65f,.35f),Offset(.5f,.64f)).forEach {p->drawCircle(color,1.8.dp.toPx(),Offset(size.width*p.x,size.height*p.y))}
                }
            }
            Surface(Modifier.align(Alignment.TopEnd).offset(x=5.dp,y=(-4).dp).size(15.dp),shape=CircleShape,color=SurfaceGreen,border=BorderStroke(1.dp,color)) {
                Box(contentAlignment=Alignment.Center) {Text(port.number.toString(),fontSize=9.sp,color=Color.White,fontWeight=FontWeight.Bold)}
            }
        }
    }
}
@Composable fun StationEquipment(station:Station,onConnector:(Connector)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth>=300.dp) 3 else 2
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            station.chargers.chunked(columns).forEach {units->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                    units.forEach {charger->
                        Surface(Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=Color(0xFF19382D),border=BorderStroke(1.dp,Color(0xFF315044))) {
                            Column(Modifier.padding(vertical=6.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(charger.number.toString(),fontSize=13.sp,fontWeight=FontWeight.Bold)
                                    Text("${charger.powerKw} кВт",color=Lime,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
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
    Surface(shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(station.title,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text("${station.city} · ДЕМО",color=Muted,fontSize=12.sp)
                }
                IconButton(onClick=onDetail) {Icon(Icons.Default.Place,"Маршрут и детали ${station.city}",tint=Lime)}
            }
            Text("Свободно ${station.availableConnectors} из ${station.connectorCount} разъёмов · 13,5 сом/кВт·ч",color=Muted,fontSize=11.sp)
            location?.let {Text("${String.format(Ru,"%.1f",distanceKm(it,station.point))} км по прямой",color=Muted,fontSize=11.sp)}
            StationEquipment(station,onConnector)
        }
    }
}
