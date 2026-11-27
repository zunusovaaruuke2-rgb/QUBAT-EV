package kg.qubat.ev

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToLong

/** Actual Android preferences/JSON and ViewModel recreation, without replacing the billing engine. */
class DriverDiscountPersistenceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as Application
    private val stores=mutableListOf<ViewModelStore>()
    private val snapshots=linkedMapOf<SharedPreferences,Map<String,*>>()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    @Before fun prepare() {
        listOf("qubat_profile","qubat_driver_discounts_v1","qubat_demo_v1").forEach{name->
            val prefs=app.getSharedPreferences(name,0);snapshots[prefs]=prefs.all.toMap();prefs.edit().clear().commit()
        }
        app.getSharedPreferences("qubat_profile",0).edit().putString("phone","+996700123456").putString("name","Discount test driver").commit()
    }
    @After fun restore() {
        main{stores.forEach{it.clear()};stores.clear()}
        snapshots.forEach{(prefs,values)->
            val edit=prefs.edit().clear()
            values.forEach{(key,value)->when(value){
                is String->edit.putString(key,value);is Long->edit.putLong(key,value);is Int->edit.putInt(key,value)
                is Boolean->edit.putBoolean(key,value);is Float->edit.putFloat(key,value)
                is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
            }}
            edit.commit()
        }
    }
    private fun vm():Pair<ViewModelStore,ChargingViewModel> {
        lateinit var store:ViewModelStore;lateinit var result:ChargingViewModel
        main{
            store=ViewModelStore();stores+=store
            result=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[ChargingViewModel::class.java]
        }
        return store to result
    }
    @Test fun discountIsSavedRestoredAndFrozenAfterOwnerDisablesIt() {
        val disk=DriverDiscountStore(app);val driverId=requireNotNull(disk.syncLocalDriver())
        val today=LocalDate.now(PARTNER_ZONE)
        val network=NativePartnerRepository.seed(today)
        val owner=NativePartnerRepository(NativePartnerRepository.accounts.first{it.id=="demo-owner-a"},network)
        val service=DriverDiscountService(owner,disk)
        service.save(null,driverId,setOf("partner-demo-a-1"),15,null)
        assertEquals(15,DriverDiscountStore(app).load().discounts.single().percent)
        val station=DemoData.stations.first{it.id=="demo-bishkek"}
        val port=station.connectors.first{it.state==ConnectorState.CONNECTED}
        val (firstStore,first)=vm()
        main{assertTrue(first.start(station,port.id,1148))}
        val json=JSONObject(app.getSharedPreferences("qubat_demo_v1",0).getString("state","{}")!!)
        assertEquals(3,json.getInt("schema"))
        assertEquals(1148L,json.getJSONArray("charges").getJSONObject(0).getLong("tariff"))
        android.os.SystemClock.sleep(1100)
        main{firstStore.clear()}
        val (_,second)=vm()
        assertEquals(1148L,second.state.value.charges.single().session.agreedTariffMinor!!)
        val rule=disk.load().discounts.single()
        service.save(rule.id,driverId,rule.pointIds,rule.percent,null,enabled=false)
        assertTrue(currentDriverDiscounts(app).isEmpty())
        assertEquals(1148L,second.state.value.charges.single().session.agreedTariffMinor!!)
        main{second.stop(port.id)}
        val stopped=second.state.value
        assertFalse(stopped.active)
        assertEquals((stopped.lastProgress.energyKwh*1148).roundToLong(),stopped.lastProgress.costMinor)
        assertEquals(150000L-stopped.lastProgress.costMinor,stopped.balanceMinor)
        main{second.stop(port.id)}
        assertEquals(stopped,second.state.value)
    }
    @Test fun legacySchemaTwoSessionKeepsItsOriginalUndiscountedPrice() {
        val station=DemoData.stations.first()
        val port=station.connectors.first{it.state==ConnectorState.CONNECTED}
        val start=System.currentTimeMillis()-1000
        val charge=JSONObject().put("id",station.id).put("connector",port.id).put("start",start)
            .put("budget",150000).put("vehicle",1).put("seconds",0).put("energy",0).put("cost",0).put("updated",start)
        val state=JSONObject().put("schema",2).put("balance",150000).put("charges",JSONArray().put(charge))
        app.getSharedPreferences("qubat_demo_v1",0).edit().putString("state",state.toString()).commit()
        val (_,model)=vm()
        val running=model.state.value.charges.single()
        assertNull(running.session.agreedTariffMinor)
        assertEquals((running.progress.energyKwh*station.tariffMinor).roundToLong(),running.progress.costMinor)
        main{model.stop(port.id)}
        val stopped=model.state.value
        main{model.stop(port.id)}
        assertEquals(stopped,model.state.value)
    }
}
