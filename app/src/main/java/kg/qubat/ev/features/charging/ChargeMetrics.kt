package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ChargeGauge(percent:Int,active:Boolean,modifier:Modifier,compact:Boolean) {
    val accent=when { percent<20 -> Color(0xFFE5484D); percent<50 -> Color(0xFFD6A016); else -> Lime }
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val scale=(maxWidth.value/150f).coerceIn(.55f,1f)
        CircularProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxSize().padding(5.dp),color=accent,trackColor=Muted.copy(alpha=.16f),strokeWidth=if(compact)8.dp else 12.dp,strokeCap=StrokeCap.Round)
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            if(!compact)CarGlyph(Modifier.size((25*scale).dp),accent)
            ChargeText("$percent%",fontSize=(if(compact)26*scale else 34*scale).sp,fontWeight=FontWeight.Black,color=accent)
            if(!compact){ChargeText(if(active)"Зарядка идёт" else "Готово к зарядке",fontSize=(10*scale).sp,color=Muted,maxLines=1)}
        }
    }
}
@Composable internal fun ChargeMetric(kind:Int,label:String,value:String,panel:Boolean,modifier:Modifier=Modifier,roomy:Boolean=false,id:String="") {
    val body:@Composable ()->Unit={
        Row(Modifier.fillMaxSize().padding(horizontal=if(roomy)10.dp else 6.dp,vertical=0.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(roomy)10.dp else 6.dp)) {
            ChargeSymbol(kind,Modifier.size(18.dp).testTag("metric-icon-$kind"))
            Column(Modifier.weight(1f)) {
                ChargeText(label,Modifier.testTag("metric-label-$kind"),fontSize=12.sp,color=Muted,maxLines=1)
                ChargeText(value,Modifier.testTag("metric-value-$kind"),fontSize=15.sp,fontWeight=FontWeight.Bold,color=metricColor(kind),maxLines=1)
            }
        }
    }
    if(panel)DepthSurface(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=SurfaceGreen){body()} else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        ChargeSymbol(kind,Modifier.size(16.dp));ChargeText(label,Modifier.weight(1f),fontSize=11.sp,color=Muted,maxLines=1)
        ChargeText(value,Modifier.testTag("card-value-$id-$kind"),fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
