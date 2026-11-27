package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration

@Composable internal fun NativeMonitor(repo:NativePartnerRepository,revision:Int,onPoint:(String)->Unit) {
    var selectedId by rememberSaveable(repo.account.id){mutableStateOf<String?>(null)}
    val allStations=remember(repo,revision){repo.stations()}
    val selected=allStations.firstOrNull{it.id==selectedId}
    if(selectedId!=null && selected==null) {
        NativePanel("Точка недоступна") {
            AppText("Выбранная точка больше недоступна. Обновите выбор.",color=Muted)
            PressTextButton(onClick={selectedId=null},modifier=Modifier.testTag("network-monitor-reset")){AppText("Все точки")}
        }
        return
    }
    val report=remember(repo,revision,selectedId){repo.monitoring(selectedId)}
    val summary=report.summary
    val stations=if(selected==null)allStations else listOf(selected)
    val ids=stations.mapTo(hashSetOf()){it.id}
    val active=remember(repo,revision,selectedId){repo.activeSessions().filter{it.stationId in ids}}
    val list=rememberLazyListState()
    LaunchedEffect(selectedId){list.scrollToItem(0)}
    var zoom by remember{mutableIntStateOf(0)}
    var focus by remember{mutableIntStateOf(0)}
    val mapStations=remember(stations,revision){stations.map{s->
        val stale=repo.isStale(s)
        val apparatus=monitorApparatuses(s).map{unit->
            val ports=unit.ports.map{p->
                Connector(p.id,p.number,if(stale||repo.isPortStale(s.id,p.id))ConnectorState.OFFLINE else when(p.status){NativePortStatus.FREE->ConnectorState.FREE;NativePortStatus.BUSY->ConnectorState.CHARGING;NativePortStatus.FAULT->ConnectorState.ERROR;NativePortStatus.UNAVAILABLE->ConnectorState.OFFLINE})
            }
            Charger("${s.id}-apparatus-${unit.number-1}",unit.number,ports)
        }
        Station(s.id,s.city,s.title,GeoPoint(s.latitude.toDouble(),s.longitude.toDouble()),apparatus,powerKw=s.powerKw.toInt())
    }}
    LazyColumn(Modifier.fillMaxSize().testTag("network-monitor-scroll"),state=list,contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item("notice"){NativeNotice()}
        item("selector"){MonitorPointSelector(allStations,selectedId){selectedId=it}}
        item("stations"){MonitorEquipmentCounts(summary)}
        item("equipment-title"){AppText(if(selected==null)"Точки и оборудование" else "Аппараты на точке",fontSize=18.sp,fontWeight=FontWeight.Bold)}
        if(selected==null) {
            if(stations.isEmpty())item("empty-points"){AppText("Доступных точек пока нет",color=Muted)}
            items(stations,key={"point-${it.id}"},contentType={"point"}){point->MonitorPointCard(point){selectedId=point.id}}
        } else {
            items(monitorApparatuses(selected),key={"apparatus-${selected.id}-${it.number}"},contentType={"apparatus"}){unit->
                MonitorApparatusCard(selected,unit,repo)
            }
            item("point-passport"){PressTextButton(onClick={onPoint(selected.id)},modifier=Modifier.testTag("network-monitor-open-point")){AppText("Паспорт точки")}}
        }
        item("power"){NativePanel(if(selected==null)"Состояние сети" else "Состояние точки"){
            val allStale=stations.isEmpty() || summary.offlineStations==stations.size
            if(allStale) {
                AppText("Нет свежих данных о зарядках",color=Muted)
                AppText("Текущая мощность: Нет данных",color=Muted)
            } else {
                AppText("Активных портов: ${summary.activePorts} · без связи: ${summary.offlineStations}")
                AppText((if(summary.offlineStations>0)"Мощность по свежим данным: " else "Текущая мощность: ")+summary.currentPowerKw.stripTrailingZeros().toPlainString()+" кВт",color=Lime)
            }
            if(summary.offlineStations>0)AppText("Точек без свежих данных: ${summary.offlineStations}",fontSize=12.sp,color=Muted)
            AppText("Состояния относятся к демонстрационному снимку. Старый сигнал не отображается зелёным.",fontSize=10.sp,color=Muted)
        }}
        if(repo.can(NativePermission.FINANCE))item("revenue"){NativeMetric("Оплаченная выручка за сегодня",nativeMoney(summary.revenueKgs),"network-daily-revenue")}
        item("map"){NativePanel(if(selected==null)"Точки на карте" else "Выбранная точка на карте"){
            if(mapStations.isNotEmpty())WorldMap(mapStations,null,null,"",Modifier.fillMaxWidth().height(230.dp).testTag("network-map"),focus,zoom,0,0,onSelect={onPoint(it.id)})
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DepthOutlinedButton(onClick={zoom++}){AppText("+")};DepthOutlinedButton(onClick={zoom--}){AppText("−")};PressTextButton(onClick={focus++}){AppText("Обзор Кыргызстана")}}
            AppText("Демо-координаты; не используйте для поездки к реальной зарядке.",fontSize=10.sp,color=Muted)
        }}
        item("active-title"){AppText("Активные сессии · ДЕМО",fontSize=20.sp,fontWeight=FontWeight.Bold)}
        if(active.isEmpty())item("no-active"){AppText("Активных демонстрационных сессий нет",color=Muted)}
        items(active,key={"session-${it.id}"},contentType={"active"}){session->NativePanel(stations.firstOrNull{it.id==session.stationId}?.title?:session.stationId){
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
