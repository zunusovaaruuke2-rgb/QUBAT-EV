package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ChargeDetail(charge:RunningCharge,state:AppState,onStop:(String)->Unit,onAdd:(()->Unit)?,modifier:Modifier=Modifier) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    val power=ChargingEngine.powerKw(state,charge)
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val roomy=maxHeight>=520.dp
    val metricsHeight=with(LocalDensity.current){(12.sp.toDp()+15.sp.toDp())*5f+20.dp}
    Column(Modifier.fillMaxSize().testTag("charge-details-${port.id}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ChargeStationCard(station,port,!roomy)
        Row(Modifier.fillMaxWidth().height(maxOf(if(roomy)214.dp else 156.dp,metricsHeight)),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            BoxWithConstraints(Modifier.weight(.42f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                val diameter=minOf(maxWidth,maxHeight,164.dp)
                ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.size(diameter).testTag("charge-gauge-${port.id}"),false)
            }
            Column(Modifier.weight(.58f).fillMaxHeight().testTag("charge-metrics-${port.id}"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                ChargeMetric(0,"Мощность","${String.format(Ru,"%.1f",power)} кВт",true,Modifier.weight(1f),roomy)
                ChargeMetric(1,"Получено","${String.format(Ru,"%.2f",charge.progress.energyKwh)} кВт·ч",true,Modifier.weight(1f),roomy)
                ChargeMetric(2,"Время",chargeTime(charge.progress.seconds),true,Modifier.weight(1f),roomy)
                ChargeMetric(3,"Стоимость",money(charge.progress.costMinor),true,Modifier.weight(1f),roomy)
            }
        }
        ChargePercentChart(charge.progress,charge.powerHistory,Modifier.weight(1f),roomy)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)){ChargeStopButton(charge.session.connectorId){onStop(charge.session.connectorId)}}
            if(onAdd!=null)DepthButton(onClick=onAdd,modifier=Modifier.size(52.dp).testTag("add-charge").appDescription("Добавить автомобиль"),shape=CircleShape,contentPadding=PaddingValues(0.dp)){AppIcon(Icons.Default.Add,null,Modifier.size(28.dp))}
        }
    }
}
}
