package kg.qubat.ev

import kotlin.math.*

enum class StationStatus(val title: String) { AVAILABLE("Свободна"), BUSY("Занята"), OFFLINE("Недоступна") }
data class GeoPoint(val lat: Double, val lon: Double)
data class City(val name: String, val point: GeoPoint)
data class Station(val id: String, val city: String, val title: String, val point: GeoPoint,
    val status: StationStatus, val powerKw: Int = 80, val tariffMinor: Long = 1350,
    val connector: String = "GB/T DC", val connectorCount: Int = 4, val availableConnectors: Int = 4)
object DemoData {
    val cities = listOf(
        City("Бишкек", GeoPoint(42.8746,74.5698)), City("Ош", GeoPoint(40.5283,72.7985)),
        City("Джалал-Абад", GeoPoint(40.9333,72.9833)), City("Каракол", GeoPoint(42.4907,78.3936)),
        City("Нарын", GeoPoint(41.4287,75.9911)), City("Талас", GeoPoint(42.5228,72.2427)),
        City("Баткен", GeoPoint(40.0626,70.8194)), City("Балыкчи", GeoPoint(42.4602,76.1871)),
        City("Токмок", GeoPoint(42.8419,75.3015)), City("Чолпон-Ата", GeoPoint(42.6494,77.0823)),
        City("Кара-Балта", GeoPoint(42.8142,73.8481)), City("Токтогул", GeoPoint(41.8744,72.9419)),
        City("Узген", GeoPoint(40.7699,73.3007)), City("Кызыл-Кия", GeoPoint(40.2568,72.1279)),
        City("Кочкор", GeoPoint(42.2155,75.7566)), City("Кара-Куль", GeoPoint(41.6311,72.6693)))
    // Fictional points near city centres. These are NOT verified charging sites.
    val stations = cities.mapIndexed { index, city ->
        val ids=listOf("bishkek","osh","jalal","karakol","naryn","talas","batken","balykchy","tokmok","cholpon","karabalta","toktogul","uzgen","kyzylkiya","kochkor","karakul")
        val titles=listOf("Север","Юг","Долина","Иссык-Куль","Тянь-Шань","Манас","Айгуль","Озеро","Чуй","Берег","Запад","Перевал","Восток","Ала-Тоо","Кочкор","Нарын-ГЭС")
        val count=if(index%2==0) 4 else 8
        val status=when { index==2 || index==10 -> StationStatus.BUSY; index==3 || index==13 -> StationStatus.OFFLINE; else -> StationStatus.AVAILABLE }
        Station("demo-${ids[index]}",city.name,"${titles[index]} · DEMO ${"%02d".format(index+1)}",city.point,status,
            connectorCount=count,availableConnectors=if(status==StationStatus.AVAILABLE) count-1 else 0)
    }
}
fun normalized(value: String) = value.lowercase(java.util.Locale.ROOT).replace('ё','е').replace('–','-').replace("джалал", "жалал").trim()
fun filterStations(stations: List<Station>, query: String, city: String?, minPower: Int, status: StationStatus?): List<Station> =
    stations.filter { (city == null || it.city == city) && it.powerKw >= minPower && (status == null || it.status == status) &&
        normalized("${it.city} ${it.title} ${it.connector}").contains(normalized(query)) }
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat-a.lat); val dLon = Math.toRadians(b.lon-a.lon)
    val h = sin(dLat/2).pow(2) + cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dLon/2).pow(2)
    return 6371 * 2 * asin(sqrt(h.coerceIn(0.0,1.0)))
}
fun nearestStation(stations: List<Station>, point: GeoPoint) = stations.minByOrNull { distanceKm(point,it.point) }
data class ChargeResult(val seconds: Long, val energyKwh: Double, val costMinor: Long, val depleted: Boolean)
fun calculateCharge(seconds: Long, powerKw: Int, tariffMinor: Long, balanceMinor: Long): ChargeResult {
    require(seconds >= 0 && powerKw > 0 && tariffMinor > 0 && balanceMinor >= 0)
    val rawEnergy = powerKw * seconds.toDouble() / 3600.0
    val cap = balanceMinor.toDouble()/tariffMinor
    val energy = min(rawEnergy, cap)
    return ChargeResult(min(seconds, ceil(cap*3600/powerKw).toLong()), energy,
        (energy*tariffMinor).roundToLong().coerceAtMost(balanceMinor), rawEnergy >= cap)
}
