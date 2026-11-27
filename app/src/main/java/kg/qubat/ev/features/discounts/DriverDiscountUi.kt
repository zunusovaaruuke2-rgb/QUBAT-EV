package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

internal val LocalDriverDiscounts=staticCompositionLocalOf<Map<String,DriverDiscountQuote>>{emptyMap()}

internal fun currentDriverDiscounts(context:android.content.Context):Map<String,DriverDiscountQuote> {
    val store=DriverDiscountStore(context)
    val id=store.syncLocalDriver()
    val ledger=store.load()
    if(id==null || ledger.discounts.none{it.driverId==id && it.enabled})return emptyMap()
    val network=NativePartnerDisk(context).load()
    return resolveDriverDiscounts(ledger,id,network.stations,DemoData.stations,java.time.LocalDate.now(PARTNER_ZONE))
}

@Composable internal fun DriverDiscountProvider(content:@Composable ()->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var quotes by remember{mutableStateOf<Map<String,DriverDiscountQuote>>(emptyMap())}
    DisposableEffect(context) {
        val discountPrefs=DriverDiscountStore(context).preferences
        val profilePrefs=context.getSharedPreferences("qubat_profile",0)
        var job:Job?=null
        fun refresh(){job?.cancel();job=scope.launch{
            val latest=withContext(Dispatchers.IO){runCatching{currentDriverDiscounts(context)}.getOrDefault(emptyMap())}
            quotes=latest
        }}
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key in setOf("ledger","phone","name","discount_driver_id"))refresh()}
        discountPrefs.registerOnSharedPreferenceChangeListener(listener)
        profilePrefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        onDispose{job?.cancel();discountPrefs.unregisterOnSharedPreferenceChangeListener(listener);profilePrefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    CompositionLocalProvider(LocalDriverDiscounts provides quotes,content=content)
}

internal fun driverRateLabel(minor:Long)=java.math.BigDecimal.valueOf(minor,2).setScale(2).toPlainString().replace('.',',')+" сом/кВт·ч"

@Composable internal fun DriverDiscountBadge(stationId:String) {
    LocalDriverDiscounts.current[stationId]?.let{quote->
        Surface(color=Lime.copy(alpha=.12f),shape=MaterialTheme.shapes.small) {
            Row(Modifier.padding(horizontal=9.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                AppText("Ваша скидка",fontSize=12.sp,color=Lime)
                AppText("−${quote.percent}%",fontSize=12.sp,color=Lime,fontWeight=FontWeight.Bold)
            }
        }
    }
}
@Composable internal fun DriverDiscountPrice(stationId:String) {
    LocalDriverDiscounts.current[stationId]?.let{quote->
        NativePanel("Ваша скидка на этой точке","driver-discount-price-$stationId") {
            DriverDiscountBadge(stationId)
            AppText("Обычная цена",fontSize=12.sp,color=Muted);AppText(driverRateLabel(quote.baseMinor),fontSize=14.sp)
            AppText("Ваша цена",fontSize=12.sp,color=Muted);AppText(driverRateLabel(quote.rateMinor),fontSize=18.sp,color=Lime,fontWeight=FontWeight.Bold)
            AppText("Скидка назначена владельцем точки",fontSize=12.sp,color=Muted)
            AppText(quote.endsOn?.let(nativeDate::format)?:"Без срока",fontSize=12.sp,color=Muted)
            AppText("ДЕМО · скидка действует на этом устройстве",fontSize=11.sp,color=Muted)
        }
    }
}
