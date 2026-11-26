package kg.qubat.ev

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MemberAccessPresentationTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val member = NativeMember("person", "demo-org-north", "Example", "example@example.invalid",
        PartnerRole.EMPLOYEE, setOf("partner-demo-a-1"), today)

    @Test fun accessStatusIncludesDatesRevocationAssignmentsAndViewPermission() {
        val view = setOf(NativePermission.VIEW)
        assertEquals("Доступ открыт", memberAccessStatus(member.copy(endsOn = today), today, view, true))
        assertEquals("Отключён", memberAccessStatus(member.copy(revoked = true), today, view, true))
        assertEquals("Срок истёк", memberAccessStatus(member.copy(endsOn = today.minusDays(1)), today, view, true))
        assertEquals("Ещё не начался", memberAccessStatus(member.copy(startsOn = today.plusDays(1)), today, view, true))
        assertEquals("Нет назначенных точек", memberAccessStatus(member, today, view, false))
        assertEquals("Без права просмотра", memberAccessStatus(member, today, emptySet(), true))
    }

    @Test fun displayedPermissionsUseTheSameRoleCeilingAndExplicitRestrictions() {
        val state = NativePartnerRepository.seed(today)
        val owner = NativePartnerRepository.accounts.first { it.id == "demo-owner-a" }
        val repo = NativePartnerRepository(owner, state)
        assertEquals(setOf(NativePermission.VIEW), repo.memberPermissions("demo-employee"))
        val index = state.members.indexOfFirst { it.id == "demo-employee" }
        state.members[index] = state.members[index].copy(rights = NativePermission.entries.toSet())
        assertEquals(setOf(NativePermission.VIEW), repo.memberPermissions("demo-employee"))
        val accountant = state.members.indexOfFirst { it.id == "demo-accountant" }
        state.members[accountant] = state.members[accountant].copy(rights = setOf(NativePermission.VIEW, NativePermission.REPORT))
        assertEquals(setOf(NativePermission.VIEW, NativePermission.REPORT), repo.memberPermissions("demo-accountant"))
    }

    @Test fun permissionDescriptionsDoNotExposeOtherOrganizationsOrAllowEmployeesToListMembers() {
        val state = NativePartnerRepository.seed(today)
        val otherOwner = NativePartnerRepository(NativePartnerRepository.accounts.first { it.id == "demo-owner-b" }, state)
        assertThrows(SecurityException::class.java) { otherOwner.memberPermissions("demo-employee") }
        val employee = NativePartnerRepository(NativePartnerRepository.accounts.first { it.id == "demo-employee" }, state)
        assertThrows(SecurityException::class.java) { employee.memberPermissions("demo-accountant") }
    }
}
