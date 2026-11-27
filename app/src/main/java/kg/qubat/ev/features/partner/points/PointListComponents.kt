package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun PointListEquipmentCounts(points:List<NativeStation>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("network-point-counts"),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        PointListCount(points.size,"Точки","points",Modifier.weight(1f))
        PointListCount(points.sumOf{it.apparatusCount},"Аппараты","apparatus",Modifier.weight(1f))
        PointListCount(points.sumOf{it.ports.size},"Разъёмы","ports",Modifier.weight(1f))
    }
}
@Composable private fun PointListCount(value:Int,label:String,tag:String,modifier:Modifier) {
    DepthSurface(modifier.fillMaxHeight(),shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            AppText(value.toString(),Modifier.testTag("network-point-count-$tag"),fontSize=22.sp,fontWeight=FontWeight.Bold,color=Lime,maxLines=1)
            AppText(label,fontSize=12.sp,maxLines=2)
        }
    }
}
@Composable internal fun PointListCard(point:NativeStation,status:String,onOpen:()->Unit) {
    val color=when(status) {
        "В сети"->Lime
        "Занят"->Color(0xFF2388CE)
        "Ошибка"->MaterialTheme.colorScheme.error
        "Данные не обновлялись","Нет связи"->Amber
        else->Muted
    }
    val label=if(status=="Данные не обновлялись")"Нет свежих данных" else status
    DepthSurface(onClick=onOpen,modifier=Modifier.fillMaxWidth().testTag("network-point-${point.id}"),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    AppText(point.title,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=2)
                    AppText("${point.city} · ${point.address}",fontSize=12.sp,color=Muted,maxLines=2)
                }
                AppIcon(Icons.Default.ChevronRight,null,tint=Muted)
            }
            Surface(shape=CircleShape,color=color.copy(alpha=.11f)) {
                Row(Modifier.padding(horizontal=10.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(Modifier.size(6.dp),shape=CircleShape,color=color){}
                    AppText(label,fontSize=12.sp,color=color,maxLines=2)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.35f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PointListParameter("Общая мощность","${networkNumber(point.powerKw)} кВт",Modifier.weight(1.25f))
                PointListParameter("Аппараты",point.apparatusCount.toString(),Modifier.weight(1f))
                PointListParameter("Разъёмы",point.ports.size.toString(),Modifier.weight(1f))
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.35f))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                AppIcon(Icons.Default.Schedule,null,Modifier.size(16.dp),tint=Muted)
                AppText("Последний сигнал: ${networkTime.format(point.lastSeen)}",Modifier.weight(1f),fontSize=11.sp,color=Muted,maxLines=2)
            }
        }
    }
}
@Composable private fun PointListParameter(label:String,value:String,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        AppText(value,fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
        AppText(label,fontSize=11.sp,color=Muted,maxLines=2)
    }
}
private fun pointStatusTitle(value:String)=if(value=="Данные не обновлялись")"Нет свежих данных" else value
private fun pointGroupTitle(value:String)=when(value){"group-north"->"Северная группа";"group-south"->"Южная группа";else->value}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PointListFilters(
    stations:List<NativeStation>,brands:List<NativeBrand>,
    status:String,group:String,owner:String,brand:String,archived:Boolean,
    ownerTitle:(String)->String,onDismiss:()->Unit,onApply:(String,String,String,String,Boolean)->Unit
) {
    var draftStatus by remember{mutableStateOf(status)}
    var draftGroup by remember{mutableStateOf(group)}
    var draftOwner by remember{mutableStateOf(owner)}
    var draftBrand by remember{mutableStateOf(brand)}
    var draftArchive by remember{mutableStateOf(archived)}
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=SurfaceGreen,modifier=Modifier.testTag("network-point-filter-sheet")) {
        Column(Modifier.fillMaxWidth().heightIn(max=680.dp).padding(horizontal=18.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Фильтры точек",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
                PressTextButton(onClick={draftStatus="Все статусы";draftGroup="Все группы";draftOwner="Все владельцы";draftBrand="Все бренды";draftArchive=false},modifier=Modifier.testTag("network-point-filter-reset")){AppText("Сбросить")}
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                NetworkChoice("Статус",draftStatus,listOf("Все статусы","В сети","Нет связи","Занят","Ошибка","Данные не обновлялись","Архив"),::pointStatusTitle,"network-point-status"){draftStatus=it}
                NetworkChoice("Группа",draftGroup,listOf("Все группы")+stations.map{it.groupId}.distinct(),::pointGroupTitle,"network-point-group"){draftGroup=it}
                NetworkChoice("Владелец",draftOwner,listOf("Все владельцы")+stations.map{it.ownerId}.distinct(),{id->if(id=="Все владельцы")id else ownerTitle(id)},"network-point-owner"){draftOwner=it}
                NetworkChoice("Бренд",draftBrand,listOf("Все бренды")+brands.map{it.id},{id->brands.firstOrNull{it.id==id}?.name?:id},"network-point-brand"){draftBrand=it}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AppText("Показывать архив",Modifier.weight(1f),fontSize=14.sp)
                    Switch(checked=draftArchive,onCheckedChange={draftArchive=it},modifier=Modifier.testTag("network-point-show-archive"))
                }
            }
            DepthButton(onClick={onApply(draftStatus,draftGroup,draftOwner,draftBrand,draftArchive)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("network-point-filter-apply")){AppText("Показать точки",fontWeight=FontWeight.SemiBold)}
        }
    }
}
