package kg.qubat.ev

import org.junit.Assert.*
import org.junit.Test

class ChargingEngineTest {
    // Calculation scenarios begin with vehicles plugged in; idle demo ports are not test connections.
    private val site=DemoData.stations.first().let { original ->
        original.copy(chargers=original.chargers.map { unit ->
            unit.copy(connectors=unit.connectors.map { port ->
                if(port.state==ConnectorState.FREE)port.copy(state=ConnectorState.CONNECTED) else port
            })
        })
    }
    private val engine=ChargingCalculator(listOf(site))
    private val a=site.chargers[0].connectors[0].id
    private val b=site.chargers[1].connectors[1].id
    private val sameA=site.chargers[3].connectors[0].id
    private val sameB=site.chargers[3].connectors[1].id
    private val epoch=1_000_000L
    private fun start(s:AppState,id:String,time:Long=epoch):AppState {
        val result=engine.start(s,site.id,id,time);assertTrue(result.started);return result.state
    }
    @Test fun twoIndependentCarsUseOneWalletAndStopIndependently() {
        var s=start(start(AppState(),a),b)
        s=engine.advance(s,epoch+60_000)
        assertEquals(2,s.charges.size);assertEquals(listOf(1800L,1800L),s.charges.map{it.progress.costMinor})
        assertEquals(146400,s.availableBalanceMinor)
        s=engine.stop(s,a,epoch+60_000)
        assertEquals(148200,s.balanceMinor);assertEquals(b,s.charges.single().session.connectorId)
        s=engine.stop(s,b,epoch+120_000)
        assertFalse(s.active);assertEquals(144600,s.balanceMinor)
        assertEquals(-5400,s.history.sumOf{it.amountMinor});assertEquals(setOf(a,b),s.completedConnectorIds)
    }
    @Test fun apparatusSharesPowerAndRestoresItWhenOnePortStops() {
        var s=start(AppState(),sameA)
        s=start(s,sameB,epoch+60_000)
        assertEquals(40.0,engine.powerKw(s,s.charges.first()),0.0)
        s=engine.advance(s,epoch+120_000)
        assertEquals(listOf(2700L,900L),s.charges.map{it.progress.costMinor})
        assertEquals(listOf(80.0,40.0),s.charges.first().powerHistory.map{it.powerKw})
        s=engine.stop(s,sameA,epoch+120_000)
        assertEquals(80.0,engine.powerKw(s,s.charges.single()),0.0)
        assertEquals(listOf(40.0,80.0),s.charges.single().powerHistory.map{it.powerKw})
        s=engine.stop(s,sameB,epoch+180_000)
        assertEquals(144600,s.balanceMinor)
    }
    @Test fun oneMinorUnitCannotBeSpentTwice() {
        var s=start(start(AppState(balanceMinor=1),a),b)
        s=engine.advance(s,epoch+1000)
        assertEquals(0,s.balanceMinor);assertFalse(s.active);assertEquals(2,s.history.size)
        assertEquals(-1,s.history.sumOf{it.amountMinor})
        assertTrue(s.history.all{it.amountMinor<=0})
        assertFalse(engine.start(s,site.id,sameA,epoch+1000).started)
    }
    @Test fun exhaustedWalletIsAllocatedDeterministicallyWithoutRoundingOverdraft() {
        val s=engine.advance(start(start(AppState(balanceMinor=55),a),b),epoch+1000)
        assertEquals(listOf(-28L,-27L),s.history.map{it.amountMinor})
        assertEquals(0,s.availableBalanceMinor)
        assertEquals(s,engine.stop(s,a,epoch+2000))
    }
    @Test fun startingSecondCarFirstSettlesElapsedFirstCar() {
        val s=start(AppState(balanceMinor=30),a)
        val result=engine.start(s,site.id,b,epoch+1000)
        assertFalse(result.started);assertEquals(0,result.state.balanceMinor);assertEquals(1,result.state.history.size)
    }
    @Test fun duplicateBusyAndThirdPortsAreRejected() {
        var s=start(AppState(),a)
        assertFalse(engine.start(s,site.id,a,epoch).started)
        assertFalse(engine.start(s,site.id,site.chargers[0].connectors[1].id,epoch).started)
        assertFalse(engine.start(s,site.id,"missing",epoch).started)
        s=start(s,b)
        assertFalse(engine.start(s,site.id,sameA,epoch).started)
        assertEquals(listOf(1,2),s.charges.map{it.session.vehicleNumber})
    }
    @Test fun stoppingTwiceCannotDeductAgainOrStopOtherVehicle() {
        var s=start(start(AppState(),a),b)
        s=engine.stop(s,a,epoch+3000)
        val again=engine.stop(s,a,epoch+3000)
        assertEquals(s,again);assertEquals(1,again.history.size);assertEquals(b,again.charges.single().session.connectorId)
    }
    @Test fun fullBatteryCompletesBothVehiclesAtHundredPercent() {
        val s=engine.advance(start(start(AppState(),a),b),epoch+10_000_000)
        assertFalse(s.active);assertEquals(20400,s.balanceMinor);assertEquals(2,s.history.size)
        assertEquals(48.0,s.lastProgress.energyKwh,0.0);assertEquals(100,demoBatteryPercent(s.lastProgress.energyKwh))
        assertEquals(2160,s.lastProgress.seconds)
    }
    @Test fun backwardsClockNeverRewindsEnergyOrCost() {
        val s=engine.advance(start(AppState(),a),epoch+60_000)
        assertEquals(s,engine.advance(s,epoch-1000))
    }
    @Test fun processCatchupMatchesRegularTicks() {
        val initial=start(start(AppState(),sameA),sameB)
        var ticking=initial
        for(i in 1..120)ticking=engine.advance(ticking,epoch+i*1000L)
        val restored=engine.advance(initial,epoch+120_000)
        assertEquals(ticking.availableBalanceMinor,restored.availableBalanceMinor)
        ticking.charges.zip(restored.charges).forEach{(x,y)->assertEquals(x.progress.costMinor,y.progress.costMinor);assertEquals(x.progress.energyKwh,y.progress.energyKwh,1e-9);assertEquals(x.progress.seconds,y.progress.seconds)}
    }
    @Test fun overlayIncludesBothActivePortsAndPreservesOtherApparatus() {
        val first=site.withConnectorSessions(mapOf(a to 56,b to 37),emptySet())
        assertEquals(ConnectorState.CHARGING,first.connectors.first{it.id==a}.state)
        assertEquals(37,first.connectors.first{it.id==b}.percent)
        assertSame(site.chargers[3],first.chargers[3])
        val stopped=site.withConnectorSessions(mapOf(b to 38),setOf(a))
        assertEquals(ConnectorState.COMPLETED,stopped.connectors.first{it.id==a}.state)
        assertEquals(ConnectorState.CHARGING,stopped.connectors.first{it.id==b}.state)
    }
    @Test fun offlineCatchupReallocatesPowerAfterFirstBatteryFills() {
        val initial=start(start(AppState(),sameA),sameB,epoch+1_800_000)
        val catchup=engine.advance(initial,epoch+5_000_000)
        var ticking=initial
        for(i in 1801..5000)ticking=engine.advance(ticking,epoch+i*1000L)
        assertFalse(catchup.active)
        assertEquals(20400,catchup.balanceMinor)
        assertEquals(ticking.balanceMinor,catchup.balanceMinor)
        assertEquals(ticking.history.map{it.amountMinor}.sorted(),catchup.history.map{it.amountMinor}.sorted())
    }
    @Test fun replacementVehicleReusesFreeSlotWithoutReusingOccupiedPort() {
        var s=start(start(AppState(),a),b)
        s=engine.stop(s,a,epoch+1000)
        assertFalse(engine.start(s,site.id,a,epoch+1000).started)
        s=start(s,sameA,epoch+1000)
        assertEquals(setOf(1,2),s.charges.map{it.session.vehicleNumber}.toSet())
        assertEquals(2,s.charges.size)
    }

    @Test fun startRequiresAnExplicitConnectedPort() {
        ConnectorState.entries.forEach { status ->
            val fixture=site.copy(chargers=site.chargers.map { unit ->
                unit.copy(connectors=unit.connectors.map { port -> if(port.id==a)port.copy(state=status) else port })
            })
            val state=AppState()
            val result=ChargingCalculator(listOf(fixture)).start(state,fixture.id,a,epoch)
            assertEquals("Start eligibility for $status",status==ConnectorState.CONNECTED,result.started)
            if(!result.started)assertEquals(state,result.state)
        }
        assertFalse(engine.start(AppState(),site.id,null,epoch).started)
        assertFalse(engine.start(AppState(),site.id,"missing",epoch).started)
    }
    @Test fun defaultEngineAlsoRequiresAConnectedPort() {
        val demo=DemoData.stations.first()
        val connected=demo.connectors.first{it.state==ConnectorState.CONNECTED}
        val free=demo.connectors.first{it.state==ConnectorState.FREE}
        assertTrue(ChargingEngine.start(AppState(),demo.id,connected.id,epoch).started)
        assertFalse(ChargingEngine.start(AppState(),demo.id,free.id,epoch).started)
        assertFalse(ChargingEngine.start(AppState(),demo.id,null,epoch).started)
    }
}
