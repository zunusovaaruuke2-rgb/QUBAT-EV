@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.*

@Composable internal fun NativeMonitor(repo:NativePartnerRepository,revision:Int,onPoint:(String)->Unit) {
    val report=remember(repo,revision){repo.monitoring()}
    val summary=report.summary
    val stations=remember(repo,revision){repo.stations()}
    val active=remember(repo,revision){repo.activeSessions()}
    var zoom by remember{mutableIntStateOf(0)}
    var focus by remember{mutableIntStateOf(0)}
    val mapStations=remember(stations,revision){stations.map{s->
        val stale=repo.isStale(s)
        val apparatus=List(s.apparatusCount){index->
            val ports=s.ports.filterIndexed{portIndex,_->portIndex%s.apparatusCount==index}.map{p->
                Connector(p.id,p.number,if(stale||repo.isPortStale(s.id,p.id))ConnectorState.OFFLINE else when(p.status){NativePortStatus.FREE->ConnectorState.FREE;NativePortStatus.BUSY->ConnectorState.CHARGING;NativePortStatus.FAULT->ConnectorState.ERROR;NativePortStatus.UNAVAILABLE->ConnectorState.OFFLINE})
            }
            Charger("${s.id}-apparatus-$index",index+1,ports)
        }
        Station(s.id,s.city,s.title,GeoPoint(s.latitude.toDouble(),s.longitude.toDouble()),apparatus,powerKw=s.powerKw.toInt())
    }}
    LazyColumn(Modifier.fillMaxSize().testTag("network-monitor-scroll"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(key="notice"){NativeNotice()}
        item(key="stations"){NativeMetric("Станции / аппараты / разъёмы","${summary.stationCount} / ${summary.apparatusCount} / ${summary.connectorCount}","network-station-count")}
        item(key="power"){NativePanel("Состояние сети"){AppText("Активных портов: ${summary.activePorts} · без связи: ${summary.offlineStations}");AppText("Текущая мощность: ${summary.currentPowerKw.stripTrailingZeros().toPlainString()} кВт",color=Lime);AppText("Состояния относятся к демонстрационному снимку. Старый сигнал не отображается зелёным.",fontSize=10.sp,color=Muted)}}
        if(repo.can(NativePermission.FINANCE))item(key="revenue"){NativeMetric("Оплаченная выручка за сегодня",nativeMoney(summary.revenueKgs),"network-daily-revenue")}
        item(key="map"){NativePanel("Назначенные станции на карте"){
            if(mapStations.isNotEmpty())WorldMap(mapStations,null,null,"",Modifier.fillMaxWidth().height(230.dp).testTag("network-map"),focus,zoom,0,0,onSelect={onPoint(it.id)})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DepthOutlinedButton(onClick={zoom++}){AppText("+")};DepthOutlinedButton(onClick={zoom--}){AppText("−")};PressTextButton(onClick={focus++}){AppText("Обзор Кыргызстана")}}
            AppText("Демо-координаты; не используйте для поездки к реальной зарядке.",fontSize=10.sp,color=Muted)
        }}
        item(key="active-title"){AppText("Активные сессии · ДЕМО",fontSize=20.sp,fontWeight=FontWeight.Bold)}
        if(active.isEmpty())item(key="no-active"){AppText("Активных демонстрационных сессий нет",color=Muted)}
        items(active,key={it.id},contentType={"active"}){session->NativePanel(stations.firstOrNull{it.id==session.stationId}?.title?:session.stationId){
            AppText("${session.portId} · ${nativeTime.format(session.startedAt)}",fontSize=11.sp,color=Muted)
            AppText(nativeEnergy(session.energyKwh),color=Lime,fontWeight=FontWeight.Bold)
            val point=stations.firstOrNull{it.id==session.stationId}
            val fresh=point!=null && !repo.isStale(point) && !repo.isPortStale(point.id,session.portId)
            AppText("Мощность: ${if(fresh)point!!.instantaneousPowerKw.stripTrailingZeros().toPlainString()+" кВт" else "Нет свежих данных"}",fontSize=12.sp)
            AppText("По снимку: ${Duration.between(session.startedAt,session.updatedAt).seconds.coerceAtLeast(0)/60} мин · клиент ${session.customerId}",fontSize=11.sp,color=Muted)
            if(repo.can(NativePermission.FINANCE))AppText("Расчётная стоимость: ${nativeMoney(session.chargedKgs)}",fontSize=12.sp)
            AppText("Источник: ${session.source} · обновлено ${nativeTime.format(session.updatedAt)}",fontSize=10.sp,color=Muted)
        }}
        items(stations.filter{repo.isStale(it)||it.errorReason!=null},key={"alert-${it.id}"},contentType={"alert"}){s->NativePanel("Тревога · ${s.title}"){
            AppText(if(repo.isStale(s))"Данные не обновлялись" else s.errorReason.orEmpty(),color=MaterialTheme.colorScheme.error)
            AppText("Последний сигнал: ${nativeTime.format(s.lastSeen)} · ${s.source}",fontSize=11.sp,color=Muted)
            PressTextButton(onClick={onPoint(s.id)}){AppText("Открыть точку")}
        }}
    }
}
