package kg.qubat.ev

import java.time.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

class DriverDiscountTest {
    private val today=LocalDate.of(2026,11,26)
    private val clock=Clock.fixed(today.atStartOfDay(PARTNER_ZONE).toInstant(),PARTNER_ZONE)
    private class Memory(var value:DiscountLedger):DiscountLedgerStore {
        override fun load()=value
        override fun update(transform:(DiscountLedger)->DiscountLedger)=transform(value).also{value=it}
    }
    private fun repo(id:String,state:NativePartnerState)=NativePartnerRepository(NativePartnerRepository.accounts.first{it.id==id},state,clock)
    private val driver=DiscountDriver("driver","+996700123456","Example")
    private fun rule(id:String="r",percent:Int=15)=DriverDiscount(id,"demo-org-north",driver.id,setOf("partner-demo-a-1"),percent,today,null)

    @Test fun phoneLookupUsesCanonicalCompleteNumbers() {
        assertEquals("+996700123456",discountPhone("0700 123 456"))
        assertEquals("+996700123456",discountPhone("+996 (700) 123-456"))
        assertEquals("+996700123456",discountPhone("996700123456"))
        listOf("","700","++996700123456","abc+996700123456","+99670012345x").forEach{assertNull(discountPhone(it))}
    }
    @Test fun priceUsesExactHalfUpMinorUnitRounding() {
        assertEquals(1148L,discountedRate(1350,15))
        assertEquals(1215L,discountedRate(1350,10))
        assertThrows(IllegalArgumentException::class.java){discountedRate(1350,100)}
    }
    @Test fun selectedDriverPointAndOrganizationAreRequired() {
        val state=NativePartnerRepository.seed(today)
        val data=DiscountLedger(listOf(driver),listOf(rule()))
        val quotes=resolveDriverDiscounts(data,driver.id,state.stations,DemoData.stations,today)
        assertEquals(setOf("demo-bishkek"),quotes.keys)
        assertEquals(1148L,quotes.getValue("demo-bishkek").rateMinor)
        assertTrue(resolveDriverDiscounts(data,"other",state.stations,DemoData.stations,today).isEmpty())
        assertTrue(resolveDriverDiscounts(data.copy(discounts=listOf(rule().copy(organizationId="demo-org-south"))),driver.id,state.stations,DemoData.stations,today).isEmpty())
    }
    @Test fun expiredDisabledFutureAndArchivedRulesCannotDiscountAndPercentagesNeverStack() {
        val state=NativePartnerRepository.seed(today)
        fun resolve(rules:List<DriverDiscount>,date:LocalDate=today)=resolveDriverDiscounts(DiscountLedger(listOf(driver),rules),driver.id,state.stations,DemoData.stations,date)
        assertTrue(resolve(listOf(rule().copy(endsOn=today.minusDays(1)))).isEmpty())
        assertTrue(resolve(listOf(rule().copy(enabled=false))).isEmpty())
        assertTrue(resolve(listOf(rule().copy(startsOn=today.plusDays(1)))).isEmpty())
        assertEquals(15,resolve(listOf(rule(),rule("second",10))).getValue("demo-bishkek").percent)
        assertEquals(15,resolve(listOf(rule().copy(endsOn=today))).getValue("demo-bishkek").percent)
        val index=state.stations.indexOfFirst{it.id=="partner-demo-a-1"}
        state.stations[index]=state.stations[index].copy(archived=true)
        assertTrue(resolve(listOf(rule())).isEmpty())
    }
    @Test fun ownerCannotGrantDiscountsOnForeignPointsAndFailuresDoNotChangeLedger() {
        val state=NativePartnerRepository.seed(today);val disk=Memory(DiscountLedger(drivers=listOf(driver)))
        val service=DriverDiscountService(repo("demo-owner-a",state),disk)
        val before=disk.value
        assertThrows(IllegalArgumentException::class.java){service.save(null,driver.id,setOf("partner-demo-b-1"),15,null)}
        assertEquals(before,disk.value)
        service.save(null,driver.id,setOf("partner-demo-a-1"),15,null)
        assertEquals(1,disk.value.discounts.size)
    }
    @Test fun delegatedEmployeeHasARealCapAndNoFinanceOrReportAccess() {
        val state=NativePartnerRepository.seed(today);val disk=Memory(DiscountLedger(drivers=listOf(driver)))
        val owner=DriverDiscountService(repo("demo-owner-a",state),disk)
        val employeeRepo=repo("demo-employee",state)
        val employee=DriverDiscountService(employeeRepo,disk)
        assertFalse(employee.canOpen())
        owner.delegate("demo-employee",setOf("partner-demo-a-1"),10)
        assertTrue(employee.canOpen());assertEquals(10,employee.maxPercent("partner-demo-a-1"))
        assertFalse(employeeRepo.can(NativePermission.FINANCE));assertFalse(employeeRepo.can(NativePermission.REPORT))
        assertThrows(IllegalArgumentException::class.java){employee.save(null,driver.id,setOf("partner-demo-a-1"),11,null)}
        employee.save(null,driver.id,setOf("partner-demo-a-1"),10,null)
        assertThrows(IllegalArgumentException::class.java){employee.save(null,driver.id,setOf("partner-demo-a-2"),10,null)}
        owner.delegate("demo-employee",emptySet(),null)
        assertFalse(employee.canOpen())
        assertThrows(IllegalArgumentException::class.java){employee.save(null,driver.id,setOf("partner-demo-a-1"),10,null)}
    }
    @Test fun revokedMembershipBlocksDelegationEvenIfAnOldPermissionRemainsStored() {
        val state=NativePartnerRepository.seed(today);val disk=Memory(DiscountLedger(drivers=listOf(driver)))
        DriverDiscountService(repo("demo-owner-a",state),disk).delegate("demo-employee",setOf("partner-demo-a-1"),10)
        val index=state.members.indexOfFirst{it.id=="demo-employee"}
        state.members[index]=state.members[index].copy(revoked=true)
        assertFalse(DriverDiscountService(repo("demo-employee",state),disk).canOpen())
    }
    @Test fun frozenRatesBillTwoCarsIndependentlyAndPreserveStopIdempotence() {
        val catalog=DemoData.stations.map{s->s.copy(chargers=s.chargers.map{u->u.copy(connectors=u.connectors.map{it.copy(state=ConnectorState.CONNECTED)})})}
        val engine=ChargingCalculator(catalog);val first=catalog.first();val second=catalog[1]
        val a=first.connectors.first().id;val b=second.connectors.first().id;val start=1000000L
        var state=engine.start(AppState(),first.id,a,start,1148,15).state
        state=engine.start(state,second.id,b,start,1350,0).state
        state=engine.advance(state,start+60000)
        assertEquals((80.0/60*1148).roundToLong(),state.charges.first{it.session.connectorId==a}.progress.costMinor)
        assertEquals(1800L,state.charges.first{it.session.connectorId==b}.progress.costMinor)
        val stopped=engine.stop(state,a,start+60000)
        assertEquals(stopped,engine.stop(stopped,a,start+60000))
        assertEquals(1350L,stopped.charges.single().session.agreedTariffMinor!!)
    }
    @Test fun unprovenRateAndNonconnectedPortAreRejected() {
        val station=DemoData.stations.first();val engine=ChargingCalculator(listOf(station))
        val connected=station.connectors.first{it.state==ConnectorState.CONNECTED}
        assertFalse(engine.start(AppState(),station.id,connected.id,1000000,100,0).started)
        assertFalse(engine.start(AppState(),station.id,connected.id,1000000,1350,15).started)
        val free=station.connectors.first{it.state==ConnectorState.FREE}
        assertFalse(engine.start(AppState(),station.id,free.id,1000000,1148,15).started)
    }
}
