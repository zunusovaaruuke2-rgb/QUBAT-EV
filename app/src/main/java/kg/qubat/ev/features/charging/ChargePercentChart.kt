package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ChargePercentChart(progress:ChargeResult,powerHistory:List<PowerSample> = emptyList(),modifier:Modifier=Modifier,roomy:Boolean=false) {
    val primary=Lime;val grid=MaterialTheme.colorScheme.outline.copy(alpha=.4f)
    val duration=maxOf(60L,((progress.seconds+59)/60)*60)
    val samples=remember(powerHistory,progress){chargePercentTimeline(progress,powerHistory)}
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val ticks=when {maxHeight>=180.dp->listOf(100,75,50,25,0);maxHeight>=130.dp->listOf(100,50,0);else->listOf(100,0)}
    DepthSurface(Modifier.fillMaxSize(),shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ChargeText("Заряд батареи (%)",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1)
                ChargeText("${demoBatteryPercent(progress.energyKwh)}% · ${chargeTime(progress.seconds)}",fontSize=11.sp,color=Lime)
            }
            Row(Modifier.weight(1f)) {
                Column(Modifier.fillMaxHeight().width(42.dp),verticalArrangement=Arrangement.SpaceBetween){ticks.forEach{ChargeText("$it%",Modifier.testTag("chart-y-$it"),fontSize=11.sp,color=Muted,maxLines=1)}}
                Box(Modifier.weight(1f).fillMaxHeight().testTag("charge-percent-chart").appDescription("Процент заряда по времени").drawWithCache {
                    val line=Path();val fill=Path();val endX=size.width*progress.seconds/duration
                    fun y(percent:Double)=size.height*(1-percent.toFloat()/100)
                    line.moveTo(0f,y(samples.first().percent));fill.moveTo(0f,size.height);fill.lineTo(0f,y(samples.first().percent))
                    samples.drop(1).forEach{p->val x=size.width*p.seconds/duration;line.lineTo(x,y(p.percent));fill.lineTo(x,y(p.percent))}
                    fill.lineTo(endX,size.height);fill.close()
                    val area=Brush.verticalGradient(listOf(primary.copy(alpha=.25f),primary.copy(alpha=.02f)))
                    onDrawBehind {
                        for(i in 0..4){val gy=size.height*i/4;drawLine(grid,Offset(0f,gy),Offset(size.width,gy),1.dp.toPx())}
                        if(progress.seconds>0){drawPath(fill,area);drawPath(line,primary,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))}
                        drawCircle(primary,3.dp.toPx(),Offset(endX,y(samples.last().percent)))
                    }
                })
            }
            Row(Modifier.fillMaxWidth().padding(start=42.dp),horizontalArrangement=Arrangement.SpaceBetween){(0..2).forEach{val second=duration*it/2;ChargeText(if(duration<3600) "%02d:%02d".format(second/60,second%60) else "%02d:%02d".format(second/3600,second/60%60),Modifier.testTag("chart-time-$it"),fontSize=11.sp,color=Muted,maxLines=1)}}
        }
    }
}
}
