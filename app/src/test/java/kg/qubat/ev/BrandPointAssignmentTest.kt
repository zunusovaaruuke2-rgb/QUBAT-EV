package kg.qubat.ev

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BrandPointAssignmentTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun owner(state: NativePartnerState) = NativePartnerRepository(
        NativePartnerRepository.accounts.first { it.id == "demo-owner-a" }, state)
    private fun addName(repo: NativePartnerRepository) =
        repo.saveBrand(NativeBrand("new-name", "demo-org-north", "Example network", "#315ACB"), "Test new name")

    @Test fun assignmentsAndReplacementPreserveAllOtherStationFields() {
        val state = NativePartnerRepository.seed(today)
        val repo = owner(state)
        addName(repo)
        val before = repo.station("partner-demo-a-1")
        val unchanged = repo.station("partner-demo-a-2")
        repo.assignBrandPoints("new-name", setOf(before.id), "Assign selected point")
        assertEquals(before.copy(brandId = "new-name"), repo.station(before.id))
        assertEquals(unchanged, repo.station(unchanged.id))
        repo.assignBrandPoints("new-name", emptySet(), "Return point to original name", "brand-north")
        assertEquals(before, repo.station(before.id))
    }

    @Test fun rejectedForeignPointOrMissingReplacementMakesNoPartialChanges() {
        val state = NativePartnerRepository.seed(today)
        val repo = owner(state)
        addName(repo)
        val before = state.stations.toList()
        assertThrows(SecurityException::class.java) {
            repo.assignBrandPoints("new-name", setOf("partner-demo-a-1", "partner-demo-b-1"), "Reject foreign point")
        }
        assertEquals(before, state.stations)
        assertThrows(IllegalArgumentException::class.java) {
            repo.assignBrandPoints("brand-north", setOf("partner-demo-a-1"), "Replacement is required")
        }
        assertEquals(before, state.stations)
    }

    @Test fun foreignNamesArchivedNamesAndReadOnlyAccountsCannotAssignPoints() {
        val state = NativePartnerRepository.seed(today)
        val repo = owner(state)
        addName(repo)
        assertThrows(SecurityException::class.java) {
            repo.assignBrandPoints("brand-south", setOf("partner-demo-a-1"), "Reject foreign name")
        }
        repo.saveBrand(NativeBrand("new-name", "demo-org-north", "Example network", "#315ACB", archived = true), "Archive name")
        assertThrows(IllegalArgumentException::class.java) {
            repo.assignBrandPoints("new-name", setOf("partner-demo-a-1"), "Reject archived name")
        }
        val observer = NativePartnerRepository(NativePartnerRepository.accounts.first { it.id == "demo-observer" }, state)
        assertThrows(SecurityException::class.java) {
            observer.assignBrandPoints("brand-north", setOf("partner-demo-a-1"), "Reject read-only user")
        }
    }
}
