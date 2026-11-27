package kg.qubat.ev

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PointTotalPowerTest {
    private fun equalsPower(expected:String,input:String) {
        val value=pointTotalPower(input)
        assertNotNull(value)
        assertEquals(0,BigDecimal(expected).compareTo(value))
    }
    @Test fun mixedApparatusPowersProduceInstalledTotal() {
        equalsPower("200","80 + 120")
        equalsPower("280","80+120+80")
        equalsPower("200","200")
    }
    @Test fun decimalPowersAreAddedExactly() {
        equalsPower("0.3","0,1 + 0.2")
        equalsPower("100","50,5 + 49.5")
        equalsPower("1",".5 + 0,5")
    }
    @Test fun incompleteOrNonpositiveValuesAreNotSaved() {
        listOf(""," ","0","-80","80+","80++120","+80","80+0","80*120","80 кВт","8e99","1".repeat(301)).forEach{
            assertNull("Invalid total: $it",pointTotalPower(it))
        }
    }
}
