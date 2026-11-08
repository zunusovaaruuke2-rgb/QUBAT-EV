package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable internal fun ChargeText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:androidx.compose.ui.unit.TextUnit=14.sp,fontWeight:FontWeight?=null,maxLines:Int=Int.MAX_VALUE) {
    AppText(text,modifier,color,fontSize,fontWeight=fontWeight,lineHeight=fontSize*1.25f,maxLines=maxLines)
}

@Composable internal fun ChargePill(text:String) {
    Surface(shape=CircleShape,color=Lime.copy(alpha=.13f)){ChargeText(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Lime)}
}
@Composable internal fun ChargeStationCard(station:Station,port:Connector?,compact:Boolean=false,selection:Boolean=false) {
    DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.padding(if(compact)6.dp else 10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(if(compact)36.dp else 44.dp).depthFrame(RoundedCornerShape(14.dp),Lime.copy(alpha=.08f),0.dp),contentAlignment=Alignment.Center){ChargeSymbol(0,Modifier.size(29.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    ChargeText(station.title,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=if(selection)2 else 1)
                    ChargeText("${station.city} · Аппарат ${port?.let{station.chargerNumber(it.id)} ?: 1}",fontSize=12.sp,color=Muted,maxLines=if(selection)2 else 1)
                    ChargeText("${station.powerKw} кВт · 13,5 сом/кВт·ч" + if(selection) "" else " · GB/T DC",fontSize=11.sp,color=Lime,maxLines=if(selection)2 else 1)
                }
                ChargePill("Демо")
            }
        }
    }
}
@Composable internal fun ChargeStopButton(id:String,onStop:()->Unit) {
    DepthOutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("stop-charge-$id")) {
        Box(Modifier.size(11.dp).depthFrame(RoundedCornerShape(2.dp),Lime,0.dp));Spacer(Modifier.width(8.dp));ChargeText("Остановить зарядку",fontWeight=FontWeight.SemiBold,fontSize=16.sp)
    }
}
internal fun chargeTime(seconds:Long)="%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
