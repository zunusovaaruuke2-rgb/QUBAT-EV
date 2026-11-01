package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ChargeContent(station:Station,port:Connector?,state:AppState,onChoose:(Connector)->Unit,onStart:()->Unit,onStop:()->Unit,onRelease:()->Unit,onStations:()->Unit) {
    val active=state.session!=null
    val unit=station.chargers.firstOrNull {charger->charger.connectors.any {it.id==port?.id}} ?: station.chargers.first()
    val percent=demoBatteryPercent(state.progress.energyKwh)
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            AppText(if(active) "Зарядка идёт" else "Начать зарядку",fontSize=28.sp,fontWeight=FontWeight.Bold)
            AppText(if(active) "Аппарат ${unit.number} · разъём ${port?.number}" else "Выберите разъём и начните зарядку",fontSize=13.sp,color=Muted)
        }
        Surface(shape=RoundedCornerShape(18.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(58.dp).background(Lime.copy(alpha=.12f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {AppIcon(Icons.Default.Place,null,tint=Lime,modifier=Modifier.size(34.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    AppText(station.title,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    AppText("${station.city} · демонстрационная станция",fontSize=11.sp,color=Muted)
                    port?.let {AppText("Аппарат ${station.chargerNumber(it.id)} · разъём ${it.number} · ${it.state.title}",fontSize=11.sp,color=if(it.state==ConnectorState.FREE)Lime else androidx.compose.ui.graphics.Color(it.state.argb))}
                }
                IconButton(onClick=onStations,enabled=!active) {AppIcon(Icons.Default.ArrowForward,"Выбрать станцию")}
            }
        }
        if(!active) {
            AppText("Выберите аппарат",fontSize=12.sp,color=Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                station.chargers.forEach {charger->FilterChip(selected=charger.id==unit.id,onClick={onChoose(charger.connectors.firstOrNull {it.state==ConnectorState.FREE} ?: charger.connectors.first())},label={AppText("${charger.number} · ${charger.powerKw} кВт",fontSize=12.sp)})}
            }
        }
        AppText("Выберите разъём",fontSize=12.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            unit.connectors.forEach {item->
                val selected=item.id==port?.id
                Surface(Modifier.weight(1f).testTag("charge-choice-${item.id}").clickable(enabled=!active) {onChoose(item)},shape=RoundedCornerShape(16.dp),color=if(selected) Lime.copy(alpha=.12f) else SurfaceGreen,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Lime else MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.padding(horizontal=8.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(48.dp)) {ConnectorBadge(unit,item) {if(!active)onChoose(item)}}
                        Column(Modifier.weight(1f)) {AppText("GB/T ${item.number}",fontSize=14.sp,fontWeight=FontWeight.Bold);AppText(item.state.title,fontSize=10.sp,color=Muted)}
                        if(selected) AppIcon(Icons.Default.CheckCircle,null,tint=Lime,modifier=Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(208.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={if(active || state.progress.seconds>0)percent/100f else 0f},modifier=Modifier.size(194.dp),color=Lime,trackColor=Lime.copy(alpha=.15f),strokeWidth=5.dp)
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if(active || state.progress.seconds>0) {
                    AppText("$percent%",fontSize=44.sp,fontWeight=FontWeight.Bold,color=Lime)
                    AppText("Заряд батареи · демо",fontSize=11.sp,color=Muted)
                    AppText("${energy(state.progress.energyKwh)} кВт·ч",fontSize=13.sp)
                } else Box(Modifier.size(126.dp).background(Lime.copy(alpha=.1f),CircleShape),contentAlignment=Alignment.Center) {ChargePlug()}
            }
        }
        AppText(if(active) "Автомобиль заряжается" else if(port?.state==ConnectorState.COMPLETED) "Сеанс завершён" else if(port?.state==ConnectorState.FREE) "Готово к зарядке" else "Разъём занят или недоступен",modifier=Modifier.align(Alignment.CenterHorizontally),fontSize=21.sp,fontWeight=FontWeight.Bold)
        AppText(if(active) "Заряд батареи: $percent% · демо" else "Подключите автомобиль и выберите свободный разъём.",modifier=Modifier.align(Alignment.CenterHorizontally),fontSize=12.sp,color=Muted)
        Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                SmallChargeInfo("80 кВт","Макс. мощность")
                SmallChargeInfo("13,5 сом/кВт·ч","Тариф")
                SmallChargeInfo("GB/T DC","Разъём")
            }
        }
        Surface(shape=RoundedCornerShape(16.dp),color=SurfaceGreen) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {AppText("Стоимость",fontWeight=FontWeight.Bold,fontSize=15.sp);AppText(money(state.progress.costMinor),fontWeight=FontWeight.Bold,color=Lime,fontSize=15.sp)}
                AppText("Расчёт по фактически полученной энергии · демо",fontSize=11.sp,color=Muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    SmallChargeInfo("%02d:%02d:%02d".format(state.progress.seconds/3600,state.progress.seconds/60%60,state.progress.seconds%60),"Время")
                    SmallChargeInfo("${energy(state.progress.energyKwh)} кВт·ч","Получено")
                    SmallChargeInfo(if(active) "80 кВт" else "0 кВт","Сейчас")
                }
            }
        }
        if(active) Button(onClick=onStop,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Amber)) {AppIcon(Icons.Default.Close,null);AppText(" Остановить симуляцию",fontWeight=FontWeight.Bold)}
        else {
            Button(onClick=onStart,enabled=port?.state==ConnectorState.FREE && state.balanceMinor>0,modifier=Modifier.fillMaxWidth().height(54.dp).testTag("start-charge"),shape=RoundedCornerShape(17.dp)) {AppIcon(Icons.Default.PlayArrow,null);AppText("Начать зарядку",fontWeight=FontWeight.Bold)}
            AppText("Учебный режим · начать симуляцию без реального подключения",fontSize=10.sp,color=Muted)
            if(port?.id in state.completedConnectorIds) TextButton(onClick=onRelease) {AppText("Освободить разъём после сеанса")}
        }
        ChargePowerChart(state.progress)
        AppText("ДЕМО · батарея 60 кВт·ч, начальный заряд 20%. Мощность модели — 80 кВт. График показывает прошедший сеанс; данные автомобиля и станции не подключены.",fontSize=10.sp,lineHeight=14.sp,color=Muted)
        AppText("Учебный баланс: ${money((state.balanceMinor-(if(active)state.progress.costMinor else 0)).coerceAtLeast(0))}",fontSize=11.sp,color=Muted)
        OutlinedButton(onClick=onStations,enabled=!active,modifier=Modifier.fillMaxWidth()) {AppText("Выбрать другую станцию")}
    }
}
@Composable private fun SmallChargeInfo(value:String,label:String) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {AppText(value,fontSize=12.sp,fontWeight=FontWeight.SemiBold);AppText(label,fontSize=10.sp,color=Muted)}
}
@Composable fun ChargePowerChart(progress:ChargeResult) {
    val localize=LocalTranslate.current
    val primary=Lime
    val grid=MaterialTheme.colorScheme.outline.copy(alpha=.45f)
    val end=(20.0+progress.energyKwh/60.0*100).coerceIn(20.0,100.0).toFloat()
    val hasData=progress.seconds>0
    var inspected by remember(progress.seconds) {mutableFloatStateOf(end)}
    Surface(shape=RoundedCornerShape(18.dp),color=SurfaceGreen) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AppText("Мощность по уровню заряда",fontWeight=FontWeight.Bold,fontSize=15.sp)
            AppText(if(hasData) "${String.format(Ru,"%.1f",inspected)}% · 80 кВт · демо" else "График появится после начала зарядки",fontSize=11.sp,color=Muted)
            Row(Modifier.height(148.dp)) {
                Column(Modifier.fillMaxHeight().width(32.dp),verticalArrangement=Arrangement.SpaceBetween) {AppText("100",fontSize=9.sp,color=Muted);AppText("50",fontSize=9.sp,color=Muted);AppText("0",fontSize=9.sp,color=Muted)}
                Canvas(Modifier.weight(1f).fillMaxHeight().testTag("charge-power-chart").semantics {contentDescription=localize(if(hasData) "Демо-график: от 20 до ${String.format(Ru,"%.1f",end)} процентов, мощность 80 киловатт" else "График мощности, пока нет данных")}) {
                    for(i in 0..4) {val y=size.height*i/4;drawLine(grid,Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
                    for(i in 0..5) {val x=size.width*i/5;drawLine(grid,Offset(x,0f),Offset(x,size.height),1.dp.toPx())}
                    if(hasData) {
                        val startX=size.width*.2f
                        val endX=size.width*end/100f
                        val y=size.height*.2f
                        val fill=Path().apply {moveTo(startX,size.height);lineTo(startX,y);lineTo(endX,y);lineTo(endX,size.height);close()}
                        drawPath(fill,Brush.verticalGradient(listOf(primary.copy(alpha=.3f),primary.copy(alpha=.01f))))
                        drawLine(primary,Offset(startX,y),Offset(endX,y),3.dp.toPx(),StrokeCap.Round)
                        drawCircle(primary,4.dp.toPx(),Offset(size.width*inspected/100f,y))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start=32.dp),horizontalArrangement=Arrangement.SpaceBetween) {(0..100 step 20).forEach {AppText("$it%",fontSize=9.sp,color=Muted)}}
            AppText("Вертикаль — кВт · горизонталь — заряд батареи",fontSize=10.sp,color=Muted)
            if(hasData && end>20f) Slider(value=inspected,onValueChange={inspected=it},valueRange=20f..end,modifier=Modifier.semantics {contentDescription=localize("Посмотреть мощность при выбранном проценте")})
        }
    }
}

@Composable private fun ChargePlug() {
    val color=Lime
    Canvas(Modifier.size(76.dp)) {
        val w=size.width;val h=size.height
        drawRoundRect(color,Offset(w*.27f,h*.12f),androidx.compose.ui.geometry.Size(w*.46f,h*.55f),androidx.compose.ui.geometry.CornerRadius(w*.12f,w*.12f))
        drawLine(color,Offset(w*.5f,h*.65f),Offset(w*.5f,h*.9f),w*.1f,StrokeCap.Round)
        val bolt=Path().apply {moveTo(w*.53f,h*.23f);lineTo(w*.39f,h*.43f);lineTo(w*.49f,h*.43f);lineTo(w*.45f,h*.56f);lineTo(w*.63f,h*.35f);lineTo(w*.52f,h*.35f);close()}
        drawPath(bolt,androidx.compose.ui.graphics.Color(0xFF092A29))
    }
}
