package kg.qubat.ev

import android.content.Intent
import android.net.Uri

fun openRoute(context:android.content.Context,s:Station,message:(String)->Unit) {
    // Official 2GIS deep link, longitude first; navigator determines the source.
    val uri=Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/to/${s.point.lon},${s.point.lat}")
    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure {message("Установите 2ГИС или выберите Google Maps")}
}
fun openGoogleRoute(context:android.content.Context,s:Station,message:(String)->Unit) {
    val uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${s.point.lat},${s.point.lon}&travelmode=driving")
    runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.onFailure {message("Не найдено приложение для маршрута")}
}
