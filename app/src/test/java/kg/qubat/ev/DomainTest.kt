package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun `sixteen demo locations provide four or eight charging places`() {
        assertEquals(16,DemoData.stations.size)
        assertEquals(16,DemoData.stations.map {it.id}.distinct().size)
        assertEquals(96,DemoData.stations.sumOf {it.chargerCount})
        assertEquals(192,DemoData.stations.sumOf {it.connectorCount})
        assertTrue(DemoData.stations.all {it.chargerCount in listOf(4,8) && it.availableConnectors in 0..it.connectorCount})
        assertTrue(DemoData.stations.all {(it.status==StationStatus.AVAILABLE)==(it.availableConnectors>0)})
        assertTrue(DemoData.stations.all {it.id.startsWith("demo-") && it.powerKw==80 && it.tariffMinor==1350L && it.connector=="GB/T DC"})
    }
    @Test fun `each apparatus has two uniquely identified ports`() {
        val ports=DemoData.stations.flatMap {it.connectors}
        assertEquals(ports.size,ports.map {it.id}.distinct().size)
        assertTrue(DemoData.stations.flatMap {it.chargers}.all {it.connectors.map {p->p.number}==listOf(1,2)})
        assertEquals(ConnectorState.entries.toSet(),ports.map {it.state}.toSet())
        assertTrue(ports.all {it.percent==null || (it.state==ConnectorState.CHARGING && it.percent in 0..100)})
    }
    @Test fun `colors follow connector states and completed differs from error by title`() {
        assertEquals(0xFF45D58B.toInt(),ConnectorState.FREE.argb)
        assertEquals(0xFFFF6868.toInt(),ConnectorState.CHARGING.argb)
        assertEquals(0xFF70AAFF.toInt(),ConnectorState.CONNECTED.argb)
        assertEquals(ConnectorState.COMPLETED.argb,ConnectorState.ERROR.argb)
        assertNotEquals(ConnectorState.COMPLETED.title,ConnectorState.ERROR.title)
    }
    @Test fun `only a free port can be selected for a new session`() {
        val site=DemoData.stations.first()
        site.connectors.forEach {port->assertEquals(port.state==ConnectorState.FREE,site.freeConnector(port.id)!=null)}
        assertNull(site.freeConnector("unknown"))
    }
    @Test fun `session overlay changes only its own port and derived count`() {
        val site=DemoData.stations.first();val port=site.freeConnector()!!
        val active=site.withConnectorSession(port.id,emptySet())
        assertEquals(ConnectorState.CHARGING,active.connectors.first {it.id==port.id}.state)
        assertEquals(site.availableConnectors-1,active.availableConnectors)
        assertEquals(site.connectors.filter {it.id!=port.id},active.connectors.filter {it.id!=port.id})
        val finished=site.withConnectorSession(null,setOf(port.id))
        assertEquals(ConnectorState.COMPLETED,finished.connectors.first {it.id==port.id}.state)
        assertNull(finished.freeConnector(port.id))
        assertEquals(site,site.withConnectorSession(null,emptySet()))
        assertEquals(ConnectorState.FREE,port.state)
    }
    @Test fun `demo locations cover the requested cities`() {
        assertTrue(DemoData.cities.map {it.name}.containsAll(listOf("Бишкек","Ош","Джалал-Абад","Каракол","Нарын","Талас","Баткен")))
        assertTrue(DemoData.cities.size>7)
    }
    @Test fun `search uses station names and ignores city and connector metadata`() {
        assertEquals("demo-bishkek",filterStations(DemoData.stations,"  СЕВЕР  ",null,0,null).single().id)
        assertEquals("demo-naryn",filterStations(DemoData.stations,"тянь–шань",null,0,null).single().id)
        assertTrue(filterStations(DemoData.stations,"Бишкек",null,0,null).isEmpty())
        assertTrue(filterStations(DemoData.stations,"Ош",null,0,null).isEmpty())
        assertTrue(filterStations(DemoData.stations,"GB/T",null,0,null).isEmpty())
    }
    @Test fun `filters combine city power status and query`() {
        assertEquals(1,filterStations(DemoData.stations,"demo","Ош",80,StationStatus.AVAILABLE).size)
        assertTrue(filterStations(DemoData.stations,"","Ош",80,StationStatus.BUSY).isEmpty())
        assertTrue(filterStations(DemoData.stations,"",null,120,null).isEmpty())
        assertEquals(12,filterStations(DemoData.stations,"",null,50,StationStatus.AVAILABLE).size)
    }
    @Test fun `unrepresented city has no invented stations`() {
        assertTrue(filterStations(DemoData.stations,"","Неизвестный город",0,null).isEmpty())
    }
    @Test fun `nearest uses countrywide coordinates`() {
        assertEquals("demo-osh",nearestStation(DemoData.stations,GeoPoint(40.53,72.80))!!.id)
        assertEquals("demo-karakol",nearestStation(DemoData.stations,GeoPoint(42.49,78.40))!!.id)
        assertNull(nearestStation(emptyList(),GeoPoint(42.0,74.0)))
    }
    @Test fun `distance is symmetric and stable at identical points`() {
        val a=DemoData.cities[0].point;val b=DemoData.cities[1].point
        assertEquals(0.0,distanceKm(a,a),0.00001)
        assertEquals(distanceKm(a,b),distanceKm(b,a),0.00001)
        assertTrue(distanceKm(a,b) in 290.0..310.0)
    }
    @Test fun `one hour at 80 kW costs 1080 som`() {
        val result=calculateCharge(3600,80,1350,150000)
        assertEquals(80.0,result.energyKwh,0.000001)
        assertEquals(108000L,result.costMinor)
        assertEquals(3600L,result.seconds)
        assertFalse(result.depleted)
    }
    @Test fun `short sessions round consistently in minor currency units`() {
        assertEquals(30L,calculateCharge(1,80,1350,150000).costMinor)
        assertEquals(1800L,calculateCharge(60,80,1350,150000).costMinor)
        assertEquals(0L,calculateCharge(0,80,1350,150000).costMinor)
    }
    @Test fun `balance depletion caps both energy and cost`() {
        val result=calculateCharge(100000,80,1350,1350)
        assertEquals(1.0,result.energyKwh,0.000001)
        assertEquals(1350L,result.costMinor)
        assertEquals(45L,result.seconds)
        assertTrue(result.depleted)
    }
    @Test fun `zero balance cannot incur cost`() {
        val result=calculateCharge(100,80,1350,0)
        assertEquals(0L,result.costMinor);assertEquals(0.0,result.energyKwh,0.0);assertTrue(result.depleted)
    }
    @Test fun `tiny remaining balance is capped exactly`() {
        val result=calculateCharge(1,80,1350,1)
        assertEquals(1L,result.costMinor);assertTrue(result.depleted)
        assertEquals(1.0/1350,result.energyKwh,0.0000001)
    }
    @Test(expected=IllegalArgumentException::class) fun `negative time rejected`() {calculateCharge(-1,80,1350,100)}
    @Test(expected=IllegalArgumentException::class) fun `invalid tariff rejected`() {calculateCharge(1,80,0,100)}
    @Test fun `demo battery percentage increases with energy and is capped`() {
        assertEquals(20,demoBatteryPercent(0.0))
        assertEquals(30,demoBatteryPercent(6.0))
        assertEquals(100,demoBatteryPercent(100.0))
        val site=DemoData.stations.first()
        val port=site.freeConnector()!!
        assertEquals(57,site.withConnectorSession(port.id,emptySet(),57).connectors.first {it.id==port.id}.percent)
    }
    @Test fun `three languages translate navigation and live connector labels`() {
        assertEquals("Map",translate("Карта",AppLanguage.EN))
        assertEquals("Капчык",translate("Кошелёк",AppLanguage.KY))
        assertEquals("Available",translate("Свободен",AppLanguage.EN))
        assertEquals("Туташкан",translate("Подключён",AppLanguage.KY))
        assertEquals("Charger 2 · connector 1 · Charging",translate("Аппарат 2 · разъём 1 · Заряжается",AppLanguage.EN))
        assertEquals("3 of 8 connectors available · 13,5 KGS/kWh",translate("Свободно 3 из 8 разъёмов · 13,5 сом/кВт·ч",AppLanguage.EN))
        assertEquals("Visa",translate("Visa",AppLanguage.KY))
        assertEquals("Карта",translate("Карта",AppLanguage.RU))
    }
}
