package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ChargeContent(station:Station,port:Connector?,state:AppState,choosing:Boolean,onChoose:(Connector)->Unit,onStart:()->Unit,onStop:(String)->Unit,onRelease:()->Unit,onStations:()->Unit,onAddVehicle:()->Unit,onCancel:()->Unit) {
    var detail by rememberSaveable{mutableStateOf<String?>(null)}
    if(state.active && !choosing) {
        val selected=if(state.charges.size==1)state.charges.single() else state.charges.firstOrNull{it.session.connectorId==detail}
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.charges.size>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Активные зарядки",Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold)
                ChargePill("${state.charges.size} ${if(state.charges.size==1)"сеанс" else "сеанса"}")
            }
            if(selected!=null) {
                if(state.charges.size>1)PressTextButton(onClick={detail=null}){AppIcon(Icons.Default.ArrowBack,null);AppText(" Активные зарядки")}
                key(selected.session.connectorId){ChargeDetail(selected,state,onStop,if(state.charges.size==1) ({detail=null;onAddVehicle()}) else null)}
            } else state.charges.forEach{charge->
                key(charge.session.connectorId){ChargeSessionCard(charge,state,{detail=charge.session.connectorId}){onStop(charge.session.connectorId)}}
            }
            AppText("Учебный баланс: ${money(state.availableBalanceMinor)}",fontSize=12.sp,color=Muted)
            AppText("ДЕМО · реальное оборудование не подключено",fontSize=10.sp,color=Muted)
        }
        return
    }
    val unit=station.chargers.firstOrNull{c->c.connectors.any{it.id==port?.id}} ?: station.chargers.first()
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            AppText(if(state.active)"Второй автомобиль" else "Начать зарядку",Modifier.weight(1f),fontSize=25.sp,fontWeight=FontWeight.Bold)
            if(state.active)PressIconButton(onClick=onCancel){AppIcon(Icons.Default.Close,"Вернуться к зарядкам")}
        }
        AppText("Выберите разъём и начните зарядку",fontSize=13.sp,color=Muted)
        ChargeStationCard(station,port)
        AppText("Выберите аппарат",fontSize=12.sp,color=Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            station.chargers.forEach{charger->DepthChip(charger.id==unit.id,{onChoose(charger.connectors.firstOrNull{it.state==ConnectorState.FREE} ?: charger.connectors.first())},label={AppText("${charger.number} · ${charger.powerKw} кВт",fontSize=12.sp)})}
        }
        AppText("Выберите разъём",fontSize=12.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            unit.connectors.forEach{item->val selected=item.id==port?.id
                DepthSurface(onClick={onChoose(item)},modifier=Modifier.weight(1f).testTag("charge-choice-${item.id}"),shape=RoundedCornerShape(17.dp),color=if(selected)Lime.copy(alpha=.1f) else SurfaceGreen,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Lime else MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(44.dp)){ConnectorBadge(unit,item){onChoose(item)}}
                        Column(Modifier.weight(1f)){AppText("GB/T ${item.number}",fontWeight=FontWeight.Bold,fontSize=14.sp);AppText(item.state.title,fontSize=10.sp,color=Muted)}
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(205.dp),contentAlignment=Alignment.Center){ChargeGauge(20,false,Modifier.size(194.dp),false)}
        port?.let{AppText("Аппарат ${station.chargerNumber(it.id)} · разъём ${it.number} · ${it.state.title}",fontSize=12.sp,color=Muted)}
        DepthButton(onClick=onStart,enabled=port?.state==ConnectorState.FREE && state.availableBalanceMinor>0 && state.charges.size<ChargingEngine.MAX_CARS,modifier=Modifier.fillMaxWidth().height(54.dp).testTag("start-charge")) {
            AppIcon(Icons.Default.PlayArrow,null);AppText("Начать зарядку",fontWeight=FontWeight.Bold)
        }
        if(port?.id in state.completedConnectorIds)PressTextButton(onClick=onRelease){AppText("Освободить разъём после сеанса")}
        DepthOutlinedButton(onClick=onStations,modifier=Modifier.fillMaxWidth()){AppText("Выбрать другую станцию")}
        AppText("Учебный баланс: ${money(state.availableBalanceMinor)}",fontSize=12.sp,color=Muted)
        AppText("ДЕМО · реальное оборудование не подключено",fontSize=10.sp,color=Muted)
    }
}
@Composable private fun ChargePill(text:String) {
    Surface(shape=CircleShape,color=Lime.copy(alpha=.13f)){AppText(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Lime)}
}
@Composable private fun ChargeStationCard(station:Station,port:Connector?) {
    DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(48.dp).depthFrame(RoundedCornerShape(14.dp),Lime.copy(alpha=.08f),0.dp),contentAlignment=Alignment.Center){ChargeSymbol(0,Modifier.size(29.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    AppText(station.title,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    AppText("${station.city} · Аппарат ${port?.let{station.chargerNumber(it.id)} ?: 1}",fontSize=11.sp,color=Muted,maxLines=1)
                    AppText("${station.powerKw} кВт · 13,5 сом/кВт·ч · GB/T DC",fontSize=10.sp,color=Lime,maxLines=1)
                }
                ChargePill("Демо")
            }
        }
    }
}
@Composable private fun ChargeDetail(charge:RunningCharge,state:AppState,onStop:(String)->Unit,onAdd:(()->Unit)?) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    val power=ChargingEngine.powerKw(state,charge)
    Column(Modifier.testTag("charge-details-${port.id}"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        ChargeStationCard(station,port)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
            Box(Modifier.weight(.48f),contentAlignment=Alignment.Center){ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.fillMaxWidth().aspectRatio(1f),false)}
            Column(Modifier.weight(.52f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                ChargeMetric(0,"Мощность","${String.format(Ru,"%.1f",power)} кВт",true)
                ChargeMetric(1,"Получено","${energy(charge.progress.energyKwh)} кВт·ч",true)
                ChargeMetric(2,"Время",chargeTime(charge.progress.seconds),true)
                ChargeMetric(3,"Стоимость",money(charge.progress.costMinor),true)
            }
        }
        ChargePowerChart(charge.progress,charge.powerHistory,power)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)){ChargeStopButton(charge.session.connectorId){onStop(charge.session.connectorId)}}
            if(onAdd!=null)DepthButton(onClick=onAdd,modifier=Modifier.size(52.dp).testTag("add-charge").appDescription("Добавить автомобиль"),shape=CircleShape,contentPadding=PaddingValues(0.dp)){AppIcon(Icons.Default.Add,null,Modifier.size(28.dp))}
        }
    }
}
@Composable private fun ChargeSessionCard(charge:RunningCharge,state:AppState,onDetails:()->Unit,onStop:()->Unit) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    DepthSurface(onClick=onDetails,modifier=Modifier.fillMaxWidth().testTag("charge-session-${port.id}"),shape=RoundedCornerShape(24.dp),color=SurfaceGreen) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                CarGlyph(Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    AppText("Автомобиль ${charge.session.vehicleNumber}",fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    AppText("${station.city} · Аппарат ${station.chargerNumber(port.id)} · GB/T ${port.number}",fontSize=10.sp,color=Muted,maxLines=2)
                }
                ChargePill("Заряжается")
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.weight(.38f).aspectRatio(1f),true)
                Column(Modifier.weight(.62f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    ChargeMetric(0,"Мощность","${String.format(Ru,"%.1f",ChargingEngine.powerKw(state,charge))} кВт",false)
                    ChargeMetric(1,"Получено","${energy(charge.progress.energyKwh)} кВт·ч",false)
                    ChargeMetric(2,"Время",chargeTime(charge.progress.seconds),false)
                    ChargeMetric(3,"Стоимость",money(charge.progress.costMinor),false)
                }
            }
            ChargeStopButton(port.id,onStop)
        }
    }
}
@Composable private fun ChargeStopButton(id:String,onStop:()->Unit) {
    DepthOutlinedButton(onClick=onStop,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("stop-charge-$id")) {
        Box(Modifier.size(11.dp).depthFrame(RoundedCornerShape(2.dp),Lime,0.dp));Spacer(Modifier.width(8.dp));AppText("Остановить зарядку",fontWeight=FontWeight.SemiBold,fontSize=14.sp)
    }
}
@Composable private fun ChargeGauge(percent:Int,active:Boolean,modifier:Modifier,compact:Boolean) {
    Box(modifier,contentAlignment=Alignment.Center) {
        CircularProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxSize().padding(5.dp),color=Lime,trackColor=Muted.copy(alpha=.16f),strokeWidth=if(compact)8.dp else 10.dp,strokeCap=StrokeCap.Round)
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(!compact)CarGlyph(Modifier.size(36.dp))
            AppText("$percent%",fontSize=if(compact)28.sp else 35.sp,fontWeight=FontWeight.Bold,color=Lime)
            if(!compact){AppText(if(active)"Зарядка идёт" else "Готово к зарядке",fontSize=11.sp,color=Muted);AppText("Демо-режим",fontSize=9.sp,color=Lime)}
        }
    }
}
@Composable private fun ChargeMetric(kind:Int,label:String,value:String,panel:Boolean) {
    val body:@Composable ()->Unit={
        Column(Modifier.padding(if(panel)6.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){ChargeSymbol(kind,Modifier.size(if(panel)17.dp else 15.dp));AppText(label,fontSize=10.sp,color=Muted)}
            AppText(value,fontSize=13.sp,fontWeight=FontWeight.Bold,color=if(panel)Lime else MaterialTheme.colorScheme.onSurface)
        }
    }
    if(panel)DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=SurfaceGreen){body()} else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){ChargeSymbol(kind,Modifier.size(15.dp));AppText(label,fontSize=10.sp,color=Muted)}
        AppText(value,fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
}
private fun chargeTime(seconds:Long)="%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
@Composable fun ChargePowerChart(progress:ChargeResult,powerHistory:List<PowerSample> = emptyList(),currentPower:Double=80.0) {
    val primary=Lime;val grid=MaterialTheme.colorScheme.outline.copy(alpha=.4f)
    val duration=maxOf(60L,((progress.seconds+59)/60)*60)
    val samples=remember(powerHistory,progress.seconds,currentPower){if(powerHistory.isEmpty())listOf(PowerSample(0,currentPower)) else powerHistory}
    DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AppText("Мощность зарядки (кВт)",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=12.sp)
                AppText("${String.format(Ru,"%.1f",currentPower)} кВт",fontSize=11.sp,color=Lime)
            }
            Row(Modifier.height(96.dp)) {
                Column(Modifier.fillMaxHeight().width(26.dp),verticalArrangement=Arrangement.SpaceBetween){listOf(80,60,40,20,0).forEach{AppText("$it",fontSize=9.sp,color=Muted)}}
                Box(Modifier.weight(1f).fillMaxHeight().testTag("charge-power-chart").appDescription("Демо-график мощности зарядки").drawWithCache {
                    val line=Path();val fill=Path();val endX=size.width*progress.seconds/duration
                    fun y(kw:Double)=size.height*(1-kw.toFloat()/80)
                    line.moveTo(0f,y(samples.first().powerKw));fill.moveTo(0f,size.height);fill.lineTo(0f,y(samples.first().powerKw))
                    var previous=samples.first().powerKw
                    samples.drop(1).filter{it.seconds<=progress.seconds}.forEach{p->val x=size.width*p.seconds/duration;line.lineTo(x,y(previous));line.lineTo(x,y(p.powerKw));fill.lineTo(x,y(previous));fill.lineTo(x,y(p.powerKw));previous=p.powerKw}
                    line.lineTo(endX,y(previous));fill.lineTo(endX,y(previous));fill.lineTo(endX,size.height);fill.close()
                    val area=Brush.verticalGradient(listOf(primary.copy(alpha=.25f),primary.copy(alpha=.02f)))
                    onDrawBehind {
                        for(i in 0..4){val gy=size.height*i/4;drawLine(grid,Offset(0f,gy),Offset(size.width,gy),1.dp.toPx())}
                        if(progress.seconds>0){drawPath(fill,area);drawPath(line,primary,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round));drawCircle(primary,3.dp.toPx(),Offset(endX,y(previous)))}
                    }
                })
            }
            Row(Modifier.fillMaxWidth().padding(start=26.dp),horizontalArrangement=Arrangement.SpaceBetween){(0..3).forEach{AppText("%02d:%02d".format(duration*it/3/60,duration*it/3%60),fontSize=9.sp,color=Muted)}}
        }
    }
}
@Composable private fun CarGlyph(modifier:Modifier) {
    val color=Lime
    Canvas(modifier) {
        val w=size.width;val h=size.height;val stroke=Stroke(w*.06f,cap=StrokeCap.Round)
        val roof=Path().apply{moveTo(w*.2f,h*.43f);lineTo(w*.3f,h*.18f);lineTo(w*.7f,h*.18f);lineTo(w*.8f,h*.43f)}
        drawPath(roof,color,style=stroke)
        drawRoundRect(color,Offset(w*.13f,h*.4f),Size(w*.74f,h*.42f),CornerRadius(w*.1f),style=stroke)
        drawLine(color,Offset(w*.23f,h*.8f),Offset(w*.23f,h*.94f),w*.09f,StrokeCap.Round);drawLine(color,Offset(w*.77f,h*.8f),Offset(w*.77f,h*.94f),w*.09f,StrokeCap.Round)
        drawLine(color,Offset(w*.22f,h*.56f),Offset(w*.31f,h*.56f),w*.06f,StrokeCap.Round);drawLine(color,Offset(w*.69f,h*.56f),Offset(w*.78f,h*.56f),w*.06f,StrokeCap.Round)
        val bolt=Path().apply{moveTo(w*.53f,h*.45f);lineTo(w*.43f,h*.62f);lineTo(w*.51f,h*.62f);lineTo(w*.47f,h*.75f);lineTo(w*.60f,h*.57f);lineTo(w*.52f,h*.57f);close()};drawPath(bolt,color)
    }
}
@Composable private fun ChargeSymbol(kind:Int,modifier:Modifier) {
    val color=Lime
    Canvas(modifier){val w=size.width;val h=size.height;val stroke=Stroke(w*.09f,cap=StrokeCap.Round)
        when(kind){
            0->{val bolt=Path().apply{moveTo(w*.6f,0f);lineTo(w*.15f,h*.57f);lineTo(w*.45f,h*.57f);lineTo(w*.36f,h);lineTo(w*.85f,h*.36f);lineTo(w*.57f,h*.36f);close()};drawPath(bolt,color)}
            1->{drawRoundRect(color,Offset(w*.08f,h*.2f),Size(w*.76f,h*.6f),CornerRadius(w*.1f),style=stroke);drawLine(color,Offset(w*.93f,h*.4f),Offset(w*.93f,h*.6f),w*.1f);drawLine(color,Offset(w*.45f,h*.35f),Offset(w*.35f,h*.52f),w*.09f);drawLine(color,Offset(w*.35f,h*.52f),Offset(w*.52f,h*.48f),w*.09f);drawLine(color,Offset(w*.52f,h*.48f),Offset(w*.42f,h*.65f),w*.09f)}
            2->{drawCircle(color,w*.42f,style=stroke);drawLine(color,center,Offset(w*.5f,h*.23f),w*.08f,StrokeCap.Round);drawLine(color,center,Offset(w*.7f,h*.6f),w*.08f,StrokeCap.Round)}
            else->{for(i in 0..2)drawOval(color,Offset(w*.12f,h*(.12f+i*.24f)),Size(w*.76f,h*.29f),style=stroke)}
        }
    }
}
