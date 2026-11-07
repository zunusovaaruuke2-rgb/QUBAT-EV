package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test

class ChargePercentTimelineTest {
    @Test fun `percentage integrates changed power and anchors actual received energy`() {
        val samples=chargePercentTimeline(ChargeResult(3600,60.0,0,false),listOf(PowerSample(0,80.0),PowerSample(1800,40.0)))
        assertEquals(listOf(0L,1800L,3600L),samples.map{it.seconds})
        assertEquals(20.0,samples[0].percent,0.00001)
        assertEquals(20+40.0/60*100,samples[1].percent,0.00001)
        assertEquals(100.0,samples.last().percent,0.00001)
    }
    @Test fun `empty history and zero duration remain finite`() {
        assertEquals(listOf(ChargePercentSample(0,20.0)),chargePercentTimeline(ChargeResult(0,0.0,0,false),emptyList()))
        val samples=chargePercentTimeline(ChargeResult(600,10.0,0,false),emptyList())
        assertEquals(20+10.0/60*100,samples.last().percent,0.00001)
    }
    @Test fun `second connector can start and updates its map station status`() {
        val station=DemoData.stations.first()
        val port=station.chargers.last{it.connectors.size==2}.connectors[1]
        val result=ChargingEngine.start(AppState(),station.id,port.id,1000)
        assertTrue(result.started)
        val marker=station.withConnectorSessions(mapOf(port.id to 25),emptySet())
        assertEquals(ConnectorState.CHARGING,marker.connectors.first{it.id==port.id}.state)
        assertEquals(station.availableConnectors-1,marker.availableConnectors)
    }
}
