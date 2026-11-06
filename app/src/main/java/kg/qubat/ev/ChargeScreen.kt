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

@Composable private fun ChargeText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:androidx.compose.ui.unit.TextUnit=14.sp,fontWeight:FontWeight?=null,maxLines:Int=Int.MAX_VALUE) {
    AppText(text,modifier,color,fontSize,fontWeight=fontWeight,lineHeight=fontSize*1.25f,maxLines=maxLines)
}

@Composable fun ChargeContent(station:Station,port:Connector?,state:AppState,choosing:Boolean,onChoose:(Connector)->Unit,onStart:()->Unit,onStop:(String)->Unit,onRelease:()->Unit,onStations:()->Unit,onAddVehicle:()->Unit,onCancel:()->Unit) {
    var detail by rememberSaveable{mutableStateOf<String?>(null)}
    if(state.active && !choosing) {
        val selected=if(state.charges.size==1)state.charges.single() else state.charges.firstOrNull{it.session.connectorId==detail}
        Column(Modifier.fillMaxSize().testTag("charging-viewport"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(state.charges.size>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ChargeText("Активные зарядки",Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.Bold)
                ChargePill("Демо · ${state.charges.size} сеанса")
                if(selected!=null)PressIconButton(onClick={detail=null}){AppIcon(Icons.Default.ArrowBack,"Активные зарядки")}
            }
            if(selected!=null) {
                key(selected.session.connectorId){ChargeDetail(selected,state,onStop,if(state.charges.size==1) ({detail=null;onAddVehicle()}) else null,Modifier.weight(1f))}
            } else state.charges.forEach{charge->
                key(charge.session.connectorId){ChargeSessionCard(charge,state,{detail=charge.session.connectorId},Modifier.weight(1f)){onStop(charge.session.connectorId)}}
            }
        }
        return
    }
    val unit=station.chargers.firstOrNull{c->c.connectors.any{it.id==port?.id}} ?: station.chargers.first()
    Column(Modifier.fillMaxSize().testTag("charge-selection"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            DepthSurface(onClick=onStations,modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp)){ChargeStationCard(station,port,true)}
            if(state.active)PressIconButton(onClick=onCancel){AppIcon(Icons.Default.Close,"Вернуться к зарядкам")}
        }
        ChargeText("1 · Выберите аппарат",fontSize=11.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            station.chargers.forEach{charger->val selected=charger.id==unit.id
                DepthSurface(onClick={onChoose(charger.connectors.firstOrNull{it.state==ConnectorState.FREE} ?: charger.connectors.first())},modifier=Modifier.width(76.dp).testTag("charge-unit-${charger.id}"),shape=RoundedCornerShape(15.dp),color=if(selected)Lime.copy(alpha=.1f) else SurfaceGreen,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Lime else MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        ChargerCabinet(charger.connectors.size,Modifier.size(width=44.dp,height=36.dp))
                        ChargeText("${charger.number} · ${charger.powerKw} кВт",fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(selected)Lime else Color.Unspecified)
                        ChargeText("${charger.connectors.size} ${if(charger.connectors.size==1)"разъём" else "разъёма"}",fontSize=8.sp,color=Muted)
                    }
                }
            }
        }
        ChargeText("2 · Выберите разъём",fontSize=11.sp,fontWeight=FontWeight.Bold)
        unit.connectors.chunked(2).forEach{ports->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ports.forEach{item->val selected=item.id==port?.id
                DepthSurface(onClick={onChoose(item)},modifier=Modifier.weight(1f).testTag("charge-choice-${item.id}"),shape=RoundedCornerShape(16.dp),color=if(selected)Lime.copy(alpha=.1f) else SurfaceGreen,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Lime else MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.width(40.dp)){ConnectorBadge(unit,item,compact=true){onChoose(item)}}
                        Column(Modifier.weight(1f)){ChargeText("GB/T ${item.number}",fontWeight=FontWeight.Bold,fontSize=13.sp);ChargeText(item.state.title,fontSize=10.sp,color=Color(item.state.argb))}
                    }
                }
            }
            if(ports.size==1 && unit.connectors.size>1)Spacer(Modifier.weight(1f))
        }}
        Spacer(Modifier.weight(1f))
        DepthButton(onClick=onStart,enabled=port?.state==ConnectorState.FREE && state.availableBalanceMinor>0 && state.charges.size<ChargingEngine.MAX_CARS,modifier=Modifier.fillMaxWidth().height(50.dp).testTag("start-charge")) {
            AppIcon(Icons.Default.PlayArrow,null);ChargeText("Начать зарядку",fontWeight=FontWeight.Bold)
        }
        if(port?.id in state.completedConnectorIds)PressTextButton(onClick=onRelease,modifier=Modifier.height(32.dp)){ChargeText("Освободить разъём после сеанса",fontSize=10.sp)}
    }
}
@Composable private fun ChargePill(text:String) {
    Surface(shape=CircleShape,color=Lime.copy(alpha=.13f)){ChargeText(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Lime)}
}
@Composable private fun ChargeStationCard(station:Station,port:Connector?,compact:Boolean=false) {
    DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=SurfaceGreen) {
        Column(Modifier.padding(if(compact)6.dp else 10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(if(compact)36.dp else 44.dp).depthFrame(RoundedCornerShape(14.dp),Lime.copy(alpha=.08f),0.dp),contentAlignment=Alignment.Center){ChargeSymbol(0,Modifier.size(29.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    ChargeText(station.title,fontSize=if(compact)13.sp else 16.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    ChargeText("${station.city} · Аппарат ${port?.let{station.chargerNumber(it.id)} ?: 1}",fontSize=if(compact)9.sp else 11.sp,color=Muted,maxLines=1)
                    ChargeText("${station.powerKw} кВт · 13,5 сом/кВт·ч · GB/T DC",fontSize=if(compact)8.sp else 10.sp,color=Lime,maxLines=1)
                }
                ChargePill("Демо")
            }
        }
    }
}
@Composable private fun ChargeDetail(charge:RunningCharge,state:AppState,onStop:(String)->Unit,onAdd:(()->Unit)?,modifier:Modifier=Modifier) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    val power=ChargingEngine.powerKw(state,charge)
    Column(modifier.fillMaxWidth().testTag("charge-details-${port.id}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ChargeStationCard(station,port)
        Row(Modifier.fillMaxWidth().weight(1.15f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(.48f),contentAlignment=Alignment.Center){ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.fillMaxWidth().aspectRatio(1f),false)}
            Column(Modifier.weight(.52f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                ChargeMetric(0,"Мощность","${String.format(Ru,"%.1f",power)} кВт",true)
                ChargeMetric(1,"Получено","${energy(charge.progress.energyKwh)} кВт·ч",true)
                ChargeMetric(2,"Время",chargeTime(charge.progress.seconds),true)
                ChargeMetric(3,"Стоимость",money(charge.progress.costMinor),true)
            }
        }
        ChargePercentChart(charge.progress,charge.powerHistory,Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)){ChargeStopButton(charge.session.connectorId){onStop(charge.session.connectorId)}}
            if(onAdd!=null)DepthButton(onClick=onAdd,modifier=Modifier.size(52.dp).testTag("add-charge").appDescription("Добавить автомобиль"),shape=CircleShape,contentPadding=PaddingValues(0.dp)){AppIcon(Icons.Default.Add,null,Modifier.size(28.dp))}
        }
    }
}
@Composable private fun ChargeSessionCard(charge:RunningCharge,state:AppState,onDetails:()->Unit,modifier:Modifier=Modifier,onStop:()->Unit) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    DepthSurface(onClick=onDetails,modifier=modifier.fillMaxWidth().testTag("charge-session-${port.id}"),shape=RoundedCornerShape(24.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                CarGlyph(Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    ChargeText("Автомобиль ${charge.session.vehicleNumber}",fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    ChargeText("${station.city} · Аппарат ${station.chargerNumber(port.id)} · GB/T ${port.number}",fontSize=10.sp,color=Muted,maxLines=2)
                }
                ChargePill("Заряжается")
            }
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.weight(.38f).aspectRatio(1f),true)
                Column(Modifier.weight(.62f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
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
        Box(Modifier.size(11.dp).depthFrame(RoundedCornerShape(2.dp),Lime,0.dp));Spacer(Modifier.width(8.dp));ChargeText("Остановить зарядку",fontWeight=FontWeight.SemiBold,fontSize=14.sp)
    }
}
@Composable private fun ChargeGauge(percent:Int,active:Boolean,modifier:Modifier,compact:Boolean) {
    Box(modifier,contentAlignment=Alignment.Center) {
        CircularProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxSize().padding(5.dp),color=Lime,trackColor=Muted.copy(alpha=.16f),strokeWidth=if(compact)8.dp else 10.dp,strokeCap=StrokeCap.Round)
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(!compact)CarGlyph(Modifier.size(36.dp))
            ChargeText("$percent%",fontSize=if(compact)28.sp else 35.sp,fontWeight=FontWeight.Bold,color=Lime)
            if(!compact){ChargeText(if(active)"Зарядка идёт" else "Готово к зарядке",fontSize=11.sp,color=Muted);ChargeText("Демо-режим",fontSize=9.sp,color=Lime)}
        }
    }
}
@Composable private fun ChargeMetric(kind:Int,label:String,value:String,panel:Boolean) {
    val body:@Composable ()->Unit={
        Column(Modifier.padding(3.dp),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){ChargeSymbol(kind,Modifier.size(12.dp));ChargeText(label,fontSize=9.sp,color=Muted)}
            ChargeText(value,fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(panel)Lime else MaterialTheme.colorScheme.onSurface)
        }
    }
    if(panel)DepthSurface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=SurfaceGreen){body()} else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        ChargeSymbol(kind,Modifier.size(13.dp));ChargeText(label,Modifier.weight(1f),fontSize=10.sp,color=Muted,maxLines=1)
        ChargeText(value,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}
private fun chargeTime(seconds:Long)="%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)
@Composable fun ChargePercentChart(progress:ChargeResult,powerHistory:List<PowerSample> = emptyList(),modifier:Modifier=Modifier) {
    val primary=Lime;val grid=MaterialTheme.colorScheme.outline.copy(alpha=.4f)
    val duration=maxOf(60L,((progress.seconds+59)/60)*60)
    val samples=remember(powerHistory,progress){chargePercentTimeline(progress,powerHistory)}
    DepthSurface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=SurfaceGreen) {
        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ChargeText("Заряд батареи (%)",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=12.sp)
                ChargeText("${demoBatteryPercent(progress.energyKwh)}% · ${chargeTime(progress.seconds)}",fontSize=10.sp,color=Lime)
            }
            Row(Modifier.weight(1f)) {
                Column(Modifier.fillMaxHeight().width(28.dp),verticalArrangement=Arrangement.SpaceBetween){listOf(100,75,50,25,0).forEach{ChargeText("$it%",fontSize=8.sp,color=Muted)}}
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
            Row(Modifier.fillMaxWidth().padding(start=28.dp),horizontalArrangement=Arrangement.SpaceBetween){(0..3).forEach{ChargeText(chargeTime(duration*it/3),fontSize=8.sp,color=Muted)}}
        }
    }
}
@Composable private fun ChargerCabinet(ports:Int,modifier:Modifier) {
    val accent=Lime
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8A9D9F),Color(0xFFF2F7F6),Color(0xFFADBABB))),Offset(w*.25f,h*.03f),Size(w*.5f,h*.94f),CornerRadius(w*.08f))
        drawRoundRect(Color(0xFF072C2D),Offset(w*.3f,h*.1f),Size(w*.4f,h*.38f),CornerRadius(w*.05f))
        drawRoundRect(accent,Offset(w*.36f,h*.16f),Size(w*.28f,h*.17f),CornerRadius(w*.02f))
        val bolt=Path().apply{moveTo(w*.54f,h*.17f);lineTo(w*.43f,h*.26f);lineTo(w*.51f,h*.26f);lineTo(w*.47f,h*.32f);lineTo(w*.58f,h*.23f);lineTo(w*.50f,h*.23f);close()};drawPath(bolt,Color(0xFF063D2C))
        drawRoundRect(accent,Offset(w*.45f,h*.57f),Size(w*.1f,h*.3f),CornerRadius(w*.03f))
        for(i in 0 until ports){val right=i%2==1;val x=if(right)w*.77f else w*.23f;val y=h*(.35f+(i/2)*.2f)
            val cable=Path().apply{moveTo(x,y);cubicTo(if(right)w*.96f else w*.04f,y,if(right)w*.96f else w*.04f,h*.84f,x,h*.72f)}
            drawPath(cable,Color(0xFF193D39),style=Stroke(w*.045f,cap=StrokeCap.Round))
            drawLine(accent,Offset(x,y),Offset(x,y+h*.13f),w*.065f,StrokeCap.Round)
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
