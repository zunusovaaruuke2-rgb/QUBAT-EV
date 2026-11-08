package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ChargeGauge(percent:Int,active:Boolean,modifier:Modifier,compact:Boolean) {
    Box(modifier,contentAlignment=Alignment.Center) {
        CircularProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxSize().padding(5.dp),color=Lime,trackColor=Muted.copy(alpha=.16f),strokeWidth=if(compact)8.dp else 10.dp,strokeCap=StrokeCap.Round)
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(!compact)CarGlyph(Modifier.size(28.dp))
            ChargeText("$percent%",fontSize=if(compact)26.sp else 30.sp,fontWeight=FontWeight.Bold,color=Lime)
            if(!compact){ChargeText(if(active)"Зарядка идёт" else "Готово к зарядке",fontSize=12.sp,color=Muted)}
        }
    }
}
@Composable internal fun ChargeMetric(kind:Int,label:String,value:String,panel:Boolean,modifier:Modifier=Modifier,roomy:Boolean=false,id:String="") {
    val body:@Composable ()->Unit={
        Row(Modifier.fillMaxSize().padding(horizontal=if(roomy)10.dp else 6.dp,vertical=0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(roomy)10.dp else 6.dp)) {
            ChargeSymbol(kind,Modifier.size(18.dp).testTag("metric-icon-$kind"))
            Column(Modifier.weight(1f)) {
                ChargeText(label,Modifier.testTag("metric-label-$kind"),fontSize=12.sp,color=Muted,maxLines=1)
                ChargeText(value,Modifier.testTag("metric-value-$kind"),fontSize=15.sp,fontWeight=FontWeight.Bold,color=Lime,maxLines=1)
            }
        }
    }
    if(panel)DepthSurface(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=SurfaceGreen){body()} else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        ChargeSymbol(kind,Modifier.size(16.dp));ChargeText(label,Modifier.weight(1f),fontSize=11.sp,color=Muted,maxLines=1)
        ChargeText(value,Modifier.testTag("card-value-$id-$kind"),fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
