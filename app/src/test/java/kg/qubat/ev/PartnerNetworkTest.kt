package kg.qubat.ev

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PartnerNetworkTest {
    private val today=LocalDate.of(2026,11,1)
    private val clock=Clock.fixed(today.atTime(12,0).atZone(PARTNER_ZONE).toInstant(),PARTNER_ZONE)
    private val a="partner-demo-a-1"
    private val b="partner-demo-b-1"
    private fun money(expected:String,value:BigDecimal?) {assertNotNull("Expected $expected",value);assertEquals(0,BigDecimal(expected).compareTo(value))}
    private fun account(id:String)=NativePartnerRepository.accounts.first{it.id==id}
    private fun repo(id:String="demo-owner-a",state:NativePartnerState=NativePartnerRepository.seed(today))=NativePartnerRepository(account(id),state,clock)
    private fun clean():NativePartnerState=NativePartnerRepository.seed(today).also{it.sessions.clear();it.payments.clear();it.expenses.clear();it.monthBills.clear()}
    private fun filter(from:LocalDate=today,to:LocalDate=from,station:String?=a)=NativeReportFilter(PartnerPeriod(from,to),stationId=station)
    private fun session(id:String="one",station:String=a,date:LocalDate=today,paid:BigDecimal?=BigDecimal("136.69"),refund:BigDecimal?=BigDecimal("10.00")):NativeSession {
        val start=date.atTime(10,0).atZone(PARTNER_ZONE).toInstant()
        val rates=NativeRateSnapshot("snapshot-original",BigDecimal("13.50"),BigDecimal("4.50"),BigDecimal("0.025"),BigDecimal("0.02"),BigDecimal("0.03"),BigDecimal("3000"),BigDecimal("0.10"),BigDecimal("0.01"))
        return NativeSession(id,station,"$station-port-1","customer-safe-1",start,start.plusSeconds(1800),BigDecimal("10.125"),NativeSessionStatus.COMPLETED,
            if(paid==null)NativePaymentStatus.UNKNOWN else NativePaymentStatus.PAID,rates,BigDecimal("136.69"),paid,refund)
    }

    @Test fun scopesSeparateOrganizationsAndPhysicalApparatusFromPorts() {
        val state=NativePartnerRepository.seed(today)
        assertEquals(3,repo(state=state).stations().size)
        assertEquals(2,repo("demo-owner-b",state).stations().size)
        assertEquals(5,repo("demo-admin",state).stations().size)
        val summary=repo("demo-admin",state).summary(filter(station=null))
        assertEquals(5,summary.apparatusCount)
        assertEquals(10,summary.connectorCount)
        assertTrue(repo(state=state).stations().all{it.apparatusCount==1 && it.ports.size==2})
    }

    @Test fun directForeignIdsAndForgedRolesAreRejected() {
        val owner=repo()
        assertThrows(SecurityException::class.java){owner.station(b)}
        assertThrows(SecurityException::class.java){owner.report(filter(station=b))}
        assertThrows(SecurityException::class.java){owner.report(filter().copy(organizationId="demo-org-south"))}
        assertThrows(SecurityException::class.java){owner.report(filter().copy(portId="$b-port-1"))}
        assertThrows(SecurityException::class.java){owner.updateTariff(NativeTariff("foreign",b,today),"Попытка")}
        assertThrows(SecurityException::class.java){owner.saveMonthBill(NativeMonthBill("foreign",b,YearMonth.from(today),BigDecimal.TEN,"x",today),"Попытка")}
        assertThrows(SecurityException::class.java){NativePartnerRepository(PartnerAccount("demo-owner-a","Подмена",PartnerRole.ADMIN),owner.state,clock).stations()}
        assertThrows(SecurityException::class.java){repo("demo-driver").stations()}
    }

    @Test fun assignedEmployeeAndObserverNeverReceiveFinancialPayloads() {
        val state=clean();state.sessions.add(session());state.sessions.add(session("foreign",b))
        val employee=repo("demo-employee",state)
        assertEquals(listOf(a),employee.stations().map{it.id})
        assertFalse(employee.can(NativePermission.REPORT))
        assertFalse(employee.can(NativePermission.FINANCE))
        assertThrows(SecurityException::class.java){employee.report(filter())}
        assertThrows(SecurityException::class.java){employee.updateStation(employee.station(a).copy(title="x"),"Попытка")}
        val observer=repo("demo-observer",state)
        val report=observer.exportReport(filter())
        assertFalse(report.financeVisible)
        assertNull(report.summary.revenueKgs)
        assertNull(report.sessions.single().rates)
        assertNull(report.sessions.single().chargedKgs)
        assertNull(report.sessions.single().paidKgs)
        assertNull(report.sessions.single().refundKgs)
        assertTrue(report.payments.isEmpty())
        assertTrue(report.expenses.isEmpty())
        assertTrue(report.monthlyFinance.isEmpty())
        assertThrows(SecurityException::class.java){observer.monthlyFinance(filter())}
        assertThrows(SecurityException::class.java){observer.session("foreign")}
    }

    @Test fun accountantCanSaveFinanceButCannotChangeHardwareOrTariffs() {
        val accountant=repo("demo-accountant",clean())
        accountant.saveExpense(NativeExpense("expense",a,today,NativeExpenseKind.OTHER,BigDecimal("25.01")),"Первичный документ")
        assertEquals("demo-accountant",accountant.state.audit.last().authorId)
        assertThrows(SecurityException::class.java){accountant.archiveStation(a,"Попытка")}
        assertThrows(SecurityException::class.java){accountant.updateTariff(NativeTariff("bad",a,today),"Попытка")}
        assertThrows(SecurityException::class.java){accountant.saveExpense(NativeExpense("foreign",b,today,NativeExpenseKind.OTHER,BigDecimal.ONE),"Попытка")}
    }

    @Test fun membershipRevocationExpiryAndRoleCapsApplyToExistingRepository() {
        val state=clean();val owner=repo(state=state);val employee=repo("demo-employee",state)
        assertTrue(employee.can(NativePermission.VIEW,a))
        owner.revokeMember("demo-employee","Сотрудник уволен")
        assertFalse(employee.can(NativePermission.VIEW,a))
        assertThrows(SecurityException::class.java){employee.stations()}
        val member=state.members.first{it.id=="demo-observer"}
        owner.inviteMember(member.copy(endsOn=today,rights=NativePermission.entries.toSet()),"Временный доступ")
        val observer=repo("demo-observer",state)
        assertFalse(observer.can(NativePermission.EDIT_TARIFF,a))
        val tomorrowClock=Clock.fixed(today.plusDays(1).atTime(1,0).atZone(PARTNER_ZONE).toInstant(),PARTNER_ZONE)
        val later=NativePartnerRepository(account("demo-observer"),state,tomorrowClock)
        assertFalse(later.can(NativePermission.VIEW))
        assertEquals(today,later.today) // fixture anchor never authorizes an expired user
    }

    @Test fun lastDayIncludesLateLocalSessionAndExcludesNextMidnight() {
        val state=clean();val day=LocalDate.of(2026,10,31)
        val late=day.atTime(23,59,59).atZone(PARTNER_ZONE).toInstant()
        val next=day.plusDays(1).atStartOfDay(PARTNER_ZONE).toInstant()
        state.sessions.add(session("late",date=day).copy(endedAt=late))
        state.sessions.add(session("next",date=day).copy(endedAt=next))
        val report=repo(state=state).report(filter(day,day))
        assertEquals(listOf("late"),report.sessions.map{it.id})
        assertTrue(late in PartnerPeriod(day,day))
        assertFalse(next in PartnerPeriod(day,day))
    }

    @Test fun reportsKeepChargedPaidRefundAndUnpaidSeparateWithExactDecimals() {
        val state=clean()
        state.sessions.add(session())
        state.sessions.add(session("part",paid=BigDecimal("50.00"),refund=BigDecimal.ZERO).copy(chargedKgs=BigDecimal("100.00")))
        val summary=repo(state=state).report(filter()).summary
        money("236.69",summary.chargedKgs)
        money("186.69",summary.paidKgs)
        money("10.00",summary.refundKgs)
        money("176.69",summary.revenueKgs)
        money("50.00",summary.unpaidKgs)
        money("20.250",summary.energyKwh)
    }

    @Test fun unknownPaymentOrRefundPropagatesInsteadOfInventingZero() {
        val state=clean();state.sessions.add(session(paid=null,refund=null))
        val owner=repo(state=state)
        val report=owner.report(filter())
        assertNull(report.summary.paidKgs)
        assertNull(report.summary.refundKgs)
        assertNull(report.summary.revenueKgs)
        assertNull(report.summary.unpaidKgs)
        assertNull(report.summary.reconciliationDifferenceKgs)
        assertNull(report.stations.single().revenueKgs)
        assertEquals(1,report.summary.unknownPaymentCount)
        val month=owner.monthlyFinance(filter(today.withDayOfMonth(1),today.withDayOfMonth(today.lengthOfMonth()))).single()
        assertFalse(month.paymentDataComplete)
        assertNull(month.revenueKgs)
        assertNull(month.estimatedProfitKgs)
        assertNull(month.actualProfitKgs)
        assertNull(session(refund=null).revenueKgs)
    }

    @Test fun missingPaymentLedgerAndOpeningBalanceRemainUnknown() {
        val state=clean();state.sessions.add(session(station=b,refund=BigDecimal.ZERO))
        val report=repo("demo-owner-b",state).report(filter(station=b))
        money("136.69",report.summary.revenueKgs)
        assertNull(report.summary.paymentLedgerKgs)
        assertNull(report.summary.reconciliationDifferenceKgs)
        assertNull(report.monthlyFinance.single().outstandingKgs)
    }

    @Test fun tariffVersionDoesNotRewriteOldBillsOrAcceptBackdatedRentChanges() {
        val state=clean();val row=session(date=today.minusDays(1));state.sessions.add(row)
        val owner=repo(state=state)
        val old=owner.tariffs(a).first()
        owner.updateTariff(old.copy(id="new-version",effectiveFrom=today,customerRate=BigDecimal("18.75"),electricityRate=BigDecimal("9.25"),rentMonthly=BigDecimal("9000")),"Новые договорные условия")
        assertEquals(row,state.sessions.single())
        money("13.50",owner.session(row.id).rates!!.customerRate)
        money("136.69",owner.session(row.id).chargedKgs)
        assertEquals(today.minusDays(1),owner.tariffs(a).first{it.id==old.id}.effectiveTo)
        assertEquals("demo-owner-a",state.audit.last().authorId)
        assertTrue(state.audit.last().before.contains("13.50"))
        assertThrows(IllegalArgumentException::class.java){owner.updateTariff(old.copy(id="backdated",effectiveFrom=today.minusDays(2)),"Задним числом")}
    }

    @Test fun fullMonthPreservesEstimateAndFactAndComputesProfitWithoutTransferDoubleCount() {
        val state=clean();val month=YearMonth.of(2026,10);state.sessions.add(session(date=month.atDay(15)))
        state.expenses.add(NativeExpense("other",a,month.atDay(15),NativeExpenseKind.OTHER,BigDecimal("5.50")))
        state.expenses.add(NativeExpense("transfer",a,month.atDay(20),NativeExpenseKind.PAYOUT,BigDecimal("20.00"),paid=true))
        val owner=repo(state=state)
        owner.saveMonthBill(NativeMonthBill("bill",a,month,BigDecimal("60.00"),"DEMO-15",month.atEndOfMonth(),true,BigDecimal("11.125"),BigDecimal("2900.00"),attachment=NativeAttachment("receipt-demo.pdf","application/pdf",123)),"Ввод демонстрационной квитанции")
        val selected=filter(month.atDay(1),month.atEndOfMonth())
        val row=owner.monthlyFinance(selected).single()
        assertTrue(row.fullMonth)
        money("45.56",row.electricityEstimateKgs)
        money("60.00",row.electricityFactKgs)
        money("1.000",row.meterDifferenceKwh)
        money("3000",row.rentEstimateKgs)
        money("2900",row.rentFactKgs)
        money("-2948.01",row.estimatedProfitKgs)
        money("-2862.45",row.actualProfitKgs)
        money("20.00",row.payoutKgs)
        assertFalse(NativeDeduction.PAYOUT in row.selectedDeductions)
        assertEquals(1,row.billPaidCount)
        assertEquals("receipt-demo.pdf",owner.report(selected).monthBills.single().attachment!!.fileName)
        assertThrows(IllegalArgumentException::class.java){owner.monthlyFinance(selected,NativePartnerRepository.defaultDeductions+NativeDeduction.PAYOUT)}
    }

    @Test fun fixedPartnerContractIsSeparateFromOwnerAndDoesNotAlsoChargePercentage() {
        val state=clean();val month=YearMonth.of(2026,10)
        for(index in state.tariffs.indices){val value=state.tariffs[index];if(value.stationId==a)state.tariffs[index]=value.copy(partnerRate=BigDecimal.ZERO,partnerFixedMonthly=BigDecimal("600"))}
        val original=session(date=month.atDay(15))
        state.sessions.add(original.copy(rates=original.rates!!.copy(partnerRate=BigDecimal.ZERO,partnerFixedMonthly=BigDecimal("600"))))
        val owner=repo(state=state)
        assertNotEquals(owner.station(a).ownerId,owner.station(a).partnerId)
        assertTrue(owner.station(a).contractId.isNotBlank())
        money("600",owner.monthlyFinance(filter(month.atDay(1),month.atEndOfMonth())).single().partnerShareKgs)
        assertThrows(IllegalArgumentException::class.java){owner.updateTariff(NativeTariff("double-contract",a,today,partnerRate=BigDecimal("0.10"),partnerFixedMonthly=BigDecimal("600")),"Некорректный договор")}
    }

    @Test fun partialMonthAndSessionFiltersNeverProrateActualReceipt() {
        val state=clean();val month=YearMonth.of(2026,10);state.sessions.add(session(date=month.atDay(15)))
        val owner=repo(state=state)
        owner.saveMonthBill(NativeMonthBill("bill",a,month,BigDecimal("60"),"DEMO",month.atEndOfMonth(),actualRentKgs=BigDecimal("2900")),"Документ")
        val partial=filter(month.atDay(10),month.atDay(20))
        val row=owner.monthlyFinance(partial).single()
        assertFalse(row.fullMonth)
        assertNull(row.electricityFactKgs)
        assertNull(row.actualProfitKgs)
        assertEquals(0,row.billCount)
        assertTrue(owner.report(partial).monthBills.isEmpty())
        val customer=filter(month.atDay(1),month.atEndOfMonth()).copy(customerId="customer-safe-1")
        assertFalse(owner.monthlyFinance(customer).single().fullMonth)
        assertNull(owner.monthlyFinance(customer).single().electricityFactKgs)
        assertNull(owner.monthlyFinance(customer).single().rentEstimateKgs)
    }

    @Test fun missingSnapshotRatesAndMissingTariffAreUnknownCosts() {
        val state=clean();state.sessions.add(session().copy(rates=null));state.tariffs.removeAll{it.stationId==a}
        val row=repo(state=state).monthlyFinance(filter()).single()
        assertNull(row.electricityEstimateKgs)
        assertNull(row.platformKgs)
        assertNull(row.rentEstimateKgs)
        assertNull(row.estimatedProfitKgs)
    }

    @Test fun pagesAreBoundedSortedAndUseSameFiltersAsReport() {
        val state=NativePartnerRepository.seed(today);val owner=repo(state=state)
        val selected=filter(today.minusDays(89),today).copy(sort=PartnerSort.ENERGY_DESC,paymentStatus=NativePaymentStatus.PAID)
        val report=owner.report(selected)
        val first=owner.sessionsPage(selected,0,7)
        assertEquals(report.sessions.take(7),first.items)
        assertEquals(report.sessions.size,first.totalCount)
        assertTrue(first.hasNext)
        assertTrue(first.items.zipWithNext().all{(left,right)->left.energyKwh>=right.energyKwh})
        assertThrows(IllegalArgumentException::class.java){owner.sessionsPage(selected,0,101)}
        assertThrows(IllegalArgumentException::class.java){owner.sessionsPage(selected,-1,20)}
    }

    @Test fun scopedMutationsAuditAndReadOnlyHistoryMasksFinance() {
        val owner=repo();val old=owner.station(a)
        owner.updateStation(old.copy(title="Обновлённая точка · ДЕМО"),"Исправлен заголовок")
        owner.archiveStation(a,"Выведено из демонстрации")
        assertFalse(owner.stations().any{it.id==a})
        assertTrue(owner.stations(true).first{it.id==a}.archived)
        assertTrue(owner.audit().last().authorId.isNotBlank())
        assertThrows(SecurityException::class.java){owner.createStation(old.copy(id="fake",organizationId="demo-org-south"),"Попытка")}
        assertThrows(IllegalArgumentException::class.java){owner.saveGrant(NativeGrant("bad","demo-org-north",setOf(a),"customer",NativeGrantKind.PERCENT,BigDecimal("101"),today,today,1),"Неверный процент")}
        owner.updateTariff(owner.tariffs(a).first().copy(id="audited-tariff",effectiveFrom=today),"Договор")
        val observer=repo("demo-observer",owner.state)
        assertTrue(observer.publicTariffs(a).isNotEmpty())
        assertTrue(observer.stationHistory(a).filter{it.action=="Версия тарифа"}.all{it.before=="Скрыто ролью" && it.after=="Скрыто ролью"})
        assertThrows(UnsupportedOperationException::class.java){owner.command(a,"remoteStart")}
    }

    @Test fun stalePortDoesNotBecomeLiveFromFreshStationHeartbeat() {
        val state=clean();val owner=repo(state=state);val old=owner.station(a)
        val point=old.copy(lastSeen=clock.instant(),ports=old.ports.map{it.copy(status=NativePortStatus.BUSY,updatedAt=clock.instant().minusSeconds(301))})
        owner.updateStation(point,"ДЕМО устаревшей телеметрии")
        assertFalse(owner.isStale(point))
        assertTrue(owner.isPortStale(a,point.ports.first().id))
        assertEquals(0,owner.summary(filter()).activePorts)
        money("0",owner.summary(filter()).currentPowerKw)
    }

    @Test fun reportUsesSelectedDeductionsAndExportIsAuditedWithinCurrentScope() {
        val state=clean();state.sessions.add(session());val owner=repo(state=state)
        val selected=setOf(NativeDeduction.ELECTRICITY)
        val report=owner.exportReport(filter(),selected)
        assertEquals(selected,report.monthlyFinance.single().selectedDeductions)
        money("81.13",report.monthlyFinance.single().estimatedProfitKgs)
        owner.recordExport(report)
        val event=state.audit.last()
        assertEquals("Экспорт Excel",event.action)
        assertEquals("demo-owner-a",event.authorId)
        assertEquals(setOf(a),event.stationIds)
        assertThrows(SecurityException::class.java){repo("demo-owner-b",state).recordExport(report)}
        val observer=repo("demo-observer",state)
        val safe=observer.exportReport(filter())
        owner.revokeMember("demo-observer","Отозван доступ")
        assertThrows(SecurityException::class.java){observer.recordExport(safe)}
    }

    @Test fun stateSerializesWithImmutableRowsAndIndependentSnapshotContainers() {
        val state=NativePartnerRepository.seed(today);val snapshot=state.snapshot()
        snapshot.audit.clear()
        assertFalse(state.audit.isEmpty())
        val bytes=ByteArrayOutputStream().also{output->ObjectOutputStream(output).use{it.writeObject(state)}}.toByteArray()
        assertTrue(bytes.size<4*1024*1024)
        val restored=ObjectInputStream(ByteArrayInputStream(bytes)).use{it.readObject() as NativePartnerState}
        assertEquals(state,restored)
        assertEquals(5,repo("demo-admin",restored).stations().size)
    }
    @Test fun missingSettlementLedgerDoesNotInventZeroPayout() {
        val state=clean()
        state.ledgerOrganizationIds.clear()
        state.sessions.add(session())
        val owner=repo(state=state)
        val month=owner.monthlyFinance(filter()).single()
        assertNull(month.payoutKgs)
        val cashDeductions=NativePartnerRepository.defaultDeductions-NativeDeduction.PARTNER_SHARE+NativeDeduction.PAYOUT
        assertNull(owner.monthlyFinance(filter(),cashDeductions).single().estimatedProfitKgs)
        state.ledgerOrganizationIds.add("demo-org-north")
        money("0.00",owner.monthlyFinance(filter()).single().payoutKgs)
        assertNull(owner.monthlyFinance(filter().copy(customerId=state.sessions.single().customerId)).single().payoutKgs)
    }

}
