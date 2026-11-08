package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun ChargeSessionCard(charge:RunningCharge,state:AppState,onDetails:()->Unit,modifier:Modifier=Modifier,onStop:()->Unit) {
    val station=DemoData.stations.first{it.id==charge.session.stationId}
    val port=station.connectors.first{it.id==charge.session.connectorId}
    DepthSurface(onClick=onDetails,modifier=modifier.fillMaxWidth().testTag("charge-session-${port.id}"),shape=RoundedCornerShape(24.dp),color=SurfaceGreen) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy=maxHeight>=230.dp
        Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                CarGlyph(Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    ChargeText("Автомобиль ${charge.session.vehicleNumber}",fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    ChargeText("${station.city} · ${station.chargerNumber(port.id)}/${port.number}",fontSize=12.sp,color=Muted,maxLines=1)
                }
                ChargePill("Заряжается")
            }
            Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                BoxWithConstraints(Modifier.weight(.34f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    val diameter=minOf(maxWidth,maxHeight,104.dp)
                    ChargeGauge(demoBatteryPercent(charge.progress.energyKwh),true,Modifier.size(diameter).testTag("charge-gauge-${port.id}"),true)
                }
                Column(Modifier.weight(.66f).testTag("charge-metrics-${port.id}"),verticalArrangement=Arrangement.spacedBy(if(roomy)4.dp else 0.dp)) {
                    ChargeMetric(0,"Мощность","${String.format(Ru,"%.1f",ChargingEngine.powerKw(state,charge))} кВт",false,roomy=roomy,id=port.id)
                    ChargeMetric(1,"Получено","${String.format(Ru,"%.2f",charge.progress.energyKwh)} кВт·ч",false,roomy=roomy,id=port.id)
                    ChargeMetric(2,"Время",chargeTime(charge.progress.seconds),false,roomy=roomy,id=port.id)
                    ChargeMetric(3,"Стоимость",money(charge.progress.costMinor),false,roomy=roomy,id=port.id)
                }
            }
            ChargeStopButton(port.id,onStop)
        }
    }
}
}
