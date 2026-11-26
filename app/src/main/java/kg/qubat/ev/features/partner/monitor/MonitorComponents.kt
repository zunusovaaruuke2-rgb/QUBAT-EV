package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun MonitorPointSelector(points:List<NativeStation>,selectedId:String?,onSelect:(String?)->Unit) {
    var open by remember{mutableStateOf(false)}
    NativePanel("Выберите точку") {
        DepthOutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().testTag("network-monitor-selector")) {
            AppIcon(Icons.Default.LocationOn,null,tint=Lime)
            Spacer(Modifier.width(8.dp))
            val point=points.firstOrNull{it.id==selectedId}
            AppText(point?.let{"${it.title} · ${it.city}"}?:"Все точки",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            AppIcon(Icons.Default.KeyboardArrowDown,null)
        }
        AppText(if(selectedId==null)"Общие показатели сети" else "Показатели выбранной точки",fontSize=12.sp,color=Muted)
    }
    if(open) {
        var query by remember{mutableStateOf("")}
        val filtered=remember(points,query){points.filter{it.title.contains(query,true)||it.city.contains(query,true)||it.address.contains(query,true)}}
        AlertDialog(onDismissRequest={open=false},title={AppText("Выберите точку")},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PressOutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().testTag("network-monitor-point-search"),singleLine=true,label={AppText("Поиск точки")})
                LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    item("all"){PressTextButton(onClick={open=false;onSelect(null)},modifier=Modifier.fillMaxWidth().testTag("network-monitor-select-all")) {
                        RadioButton(selected=selectedId==null,onClick=null);AppText("Все точки",Modifier.weight(1f))
                    }}
                    items(filtered,key={it.id}){point->PressTextButton(onClick={open=false;onSelect(point.id)},modifier=Modifier.fillMaxWidth().testTag("network-monitor-select-${point.id}")) {
                        RadioButton(selected=selectedId==point.id,onClick=null)
                        Column(Modifier.weight(1f)) {
                            AppText(point.title,fontWeight=FontWeight.SemiBold)
                            AppText("${point.city} · ${point.address}",fontSize=11.sp,color=Muted)
                        }
                    }}
                    if(filtered.isEmpty())item("empty"){AppText("Точки не найдены",color=Muted)}
                }
            }
        },confirmButton={PressTextButton(onClick={open=false}){AppText("Закрыть")}})
    }
}

@Composable internal fun MonitorEquipmentCounts(summary:NativeSummary) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("network-station-count"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        MonitorCount(summary.stationCount,"Точки",Icons.Default.LocationOn,"points",Modifier.weight(1f))
        MonitorCount(summary.apparatusCount,"Аппараты",Icons.Default.EvStation,"apparatus",Modifier.weight(1f))
        MonitorCount(summary.connectorCount,"Разъёмы",Icons.Default.Power,"ports",Modifier.weight(1f))
    }
}

@Composable private fun MonitorCount(value:Int,label:String,icon:ImageVector,tag:String,modifier:Modifier) {
    DepthSurface(modifier.fillMaxHeight(),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
            AppIcon(icon,null,Modifier.size(24.dp),tint=Lime)
            AppText(value.toString(),Modifier.testTag("network-monitor-count-$tag"),fontSize=28.sp,fontWeight=FontWeight.Bold,color=Lime,maxLines=1)
            AppText(label,fontSize=12.sp,maxLines=2)
        }
    }
}

@Composable internal fun MonitorPointCard(point:NativeStation,onSelect:()->Unit) {
    DepthSurface(onClick=onSelect,modifier=Modifier.fillMaxWidth().testTag("network-monitor-point-${point.id}"),shape=RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AppIcon(Icons.Default.LocationOn,null,Modifier.size(26.dp),tint=Lime)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                AppText("${point.title} · ${point.city}",fontSize=16.sp,fontWeight=FontWeight.Bold)
                AppText("Аппаратов: ${point.apparatusCount} · Разъёмов: ${point.ports.size}",fontSize=12.sp,color=Muted)
            }
            AppIcon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}

@Composable internal fun MonitorApparatusCard(point:NativeStation,unit:MonitorApparatus,repo:NativePartnerRepository) {
    NativePanel("Аппарат №${unit.number}","network-monitor-apparatus-${point.id}-${unit.number}") {
        AppText("Разъёмов: ${unit.ports.size}",fontSize=12.sp,color=Muted)
        if(unit.ports.isEmpty())AppText("Разъёмы не добавлены",fontSize=12.sp,color=Muted)
        unit.ports.forEach{port->
            val stale=repo.isPortStale(point.id,port.id)
            val label=if(stale)"Нет свежих данных" else when(port.status) {
                NativePortStatus.FREE->"Свободен"
                NativePortStatus.BUSY->"Заряжается"
                NativePortStatus.FAULT->"Ошибка"
                NativePortStatus.UNAVAILABLE->"Недоступен"
            }
            val color=if(stale)Muted else when(port.status) {
                NativePortStatus.FREE->Lime
                NativePortStatus.BUSY->Color(0xFF2388CE)
                NativePortStatus.FAULT->MaterialTheme.colorScheme.error
                NativePortStatus.UNAVAILABLE->Muted
            }
            Row(Modifier.fillMaxWidth().testTag("network-monitor-port-${port.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AppIcon(Icons.Default.Power,null,Modifier.size(20.dp),tint=color)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    AppText("${port.type} · №${port.number}",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    AppText("Макс. мощность: ${networkNumber(port.maxPowerKw)} кВт",fontSize=11.sp,color=Muted)
                }
                Surface(shape=CircleShape,color=color.copy(alpha=.1f)) {
                    AppText(label,Modifier.widthIn(max=120.dp).padding(horizontal=8.dp,vertical=5.dp),fontSize=11.sp,color=color,maxLines=2)
                }
            }
        }
    }
}
