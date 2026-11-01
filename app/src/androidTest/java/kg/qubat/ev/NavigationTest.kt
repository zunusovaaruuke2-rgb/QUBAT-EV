package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Press
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import android.view.View
import android.view.ViewGroup
import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun step(name: String, block: () -> Unit) {
        try { block() } catch (failure: Throwable) {
            throw AssertionError("QUBAT UI step: $name — ${failure.message}",failure)
        }
    }
    private fun visible(text:String) {
        val node=compose.onNodeWithText(text,substring=true)
        try {node.assertIsDisplayed()} catch(_:AssertionError) {node.performScrollTo().assertIsDisplayed()}
    }
    private fun findMapView(view:View):MapView? {
        if(view is MapView) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) findMapView(view.getChildAt(index))?.let {return it}
        return null
    }
    @Test fun mainNavigationAndDemoDisclosure() {
        step("Главная и раскрытие демо-данных") {
            compose.onNodeWithContentDescription("Фильтры карты").assertIsDisplayed().performClick()
            compose.onNodeWithText("Фильтры").assertIsDisplayed()
            compose.onNodeWithText("Показать станции · 16").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Моя позиция").assertIsDisplayed()
            val map=compose.onNodeWithTag("world-map")
            compose.waitUntil(45000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("карта загружена")}
            step("Дневная и ночная темы карты") {
                val switch=if(compose.onAllNodesWithContentDescription("Ночная тема").fetchSemanticsNodes().isNotEmpty()) "Ночная тема" else "Дневная тема"
                compose.onNodeWithContentDescription(switch).performClick()
                compose.waitForIdle()
                compose.waitUntil(45000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("карта загружена")}
                compose.onNodeWithContentDescription(if(switch=="Ночная тема") "Дневная тема" else "Ночная тема").assertIsDisplayed().performClick()
                compose.waitForIdle()
                compose.waitUntil(45000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("карта загружена")}
            }
            var nativeMap:MapLibreMap?=null
            lateinit var nativeView:MapView
            compose.runOnIdle {nativeView=findMapView(compose.activity.window.decorView)!!;nativeView.getMapAsync {nativeMap=it}}
            compose.waitUntil(10000) {nativeMap!=null}
            fun zoom()=compose.runOnIdle {nativeMap!!.zoom}
            fun centre()=compose.runOnIdle {nativeMap!!.projection.fromScreenLocation(PointF(nativeView.width/2f,nativeView.height/2f))}
            step("Карта показывает аппараты и разъёмы") {
                val pixel=compose.runOnIdle {val p=DemoData.stations.first().point;nativeMap!!.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(p.lat,p.lon))}
                val markerOffset=20*compose.activity.resources.displayMetrics.density
                map.performTouchInput {click(Offset(pixel.x,pixel.y-markerOffset))}
                compose.waitUntil(10000) {compose.onAllNodesWithTag("map-station-card").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("port-number-demo-bishkek-unit-1-port-1",useUnmergedTree=true).assertTextEquals("1").assertIsDisplayed()
                compose.onNodeWithTag("port-number-demo-bishkek-unit-1-port-2",useUnmergedTree=true).assertTextEquals("2").assertIsDisplayed()
                compose.onNodeWithTag("connector-demo-bishkek-unit-1-port-1").assertIsDisplayed().performClick()
                compose.onNodeWithTag("connector-dialog-state").assertTextEquals("Свободен")
                compose.onNodeWithText("Закрыть").performClick()
                compose.onNodeWithContentDescription("Закрыть карточку").performClick()
            }
            val initial=zoom()
            step("Увеличение масштаба карты") {
                compose.onNodeWithContentDescription("Увеличить карту").performClick()
                try {compose.waitUntil(10000) {zoom()>initial+0.5}}
                catch(failure:Throwable) {throw AssertionError("Масштаб до: $initial; после: ${zoom()}; ${map.fetchSemanticsNode().config[SemanticsProperties.StateDescription]}",failure)}
            }
            val initialLongitude=centre().longitude
            step("Перемещение нативной карты") {
                onView(isAssignableFrom(MapView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,GeneralLocation.CENTER,GeneralLocation.CENTER_LEFT,Press.FINGER))
                try {compose.waitUntil(10000) {kotlin.math.abs(centre().longitude-initialLongitude)>0.001}}
                catch(failure:Throwable) {throw AssertionError("Долгота до: $initialLongitude; центр экрана после: ${centre()}",failure)}
            }
            fun pinch(startRadius:Float,endRadius:Float,shiftX:Float=0f,shiftY:Float=0f) {
                map.performTouchInput {
                    val origin=Offset(width*.35f,height*.38f)
                    down(0,origin-Offset(width*startRadius,0f));down(1,origin+Offset(width*startRadius,0f))
                    for(index in 1..20) {
                        val fraction=index/20f
                        // Start translation after the pinch has crossed touch slop.
                        val shiftFraction=((index-5)/15f).coerceIn(0f,1f)
                        val focus=origin+Offset(width*shiftX*shiftFraction,height*shiftY*shiftFraction)
                        val radius=width*(startRadius+(endRadius-startRadius)*fraction)
                        updatePointerTo(0,focus-Offset(radius,0f))
                        updatePointerTo(1,focus+Offset(radius,0f))
                        move(24)
                    }
                    up(0);up(1)
                }
            }
            fun anchor()=compose.runOnIdle {nativeMap!!.projection.fromScreenLocation(PointF(nativeView.width*.35f,nativeView.height*.38f))}
            fun assertAnchor(point:org.maplibre.android.geometry.LatLng,x:Float=.35f,y:Float=.38f) {
                val error=compose.runOnIdle {
                    val actual=nativeMap!!.projection.toScreenLocation(point)
                    kotlin.math.hypot(actual.x-nativeView.width*x,actual.y-nativeView.height*y)
                }
                val tolerance=12*compose.activity.resources.displayMetrics.density
                org.junit.Assert.assertTrue("Точка между пальцами сместилась на $error px (допуск $tolerance)",error<=tolerance)
            }
            step("Приближение сохраняет точку между пальцами") {
                val before=zoom();val fixed=anchor()
                pinch(.07f,.20f)
                compose.waitUntil(10000) {zoom()>before+0.4}
                assertAnchor(fixed)
            }
            step("Отдаление сохраняет точку между пальцами") {
                val before=zoom();val fixed=anchor()
                pinch(.20f,.07f)
                compose.waitUntil(10000) {zoom()<before-0.4}
                assertAnchor(fixed)
            }
            step("Одновременный сдвиг и масштаб без скачка") {
                val fixed=anchor()
                pinch(.07f,.20f,.08f,.06f)
                assertAnchor(fixed,.43f,.44f)
            }
            compose.onNodeWithContentDescription("Уменьшить карту").performClick()

        }
        step("Поиск станции на карте") {
            compose.onNodeWithContentDescription("Поиск станции").performClick()
            compose.onNodeWithTag("station-search-field").performTextInput("Север")
            compose.onNodeWithTag("station-search-field").performImeAction()
            compose.onNodeWithText("Север · DEMO 01").assertIsDisplayed().performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("map-station-card").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Закрыть карточку").performClick()
            compose.onNodeWithContentDescription("Закрыть поиск").performClick()
        }
        step("Поиск только по названию станции и пустое состояние") {
            compose.onNodeWithText("Список",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Станции Кыргызстана").assertDoesNotExist()
            compose.onNodeWithText("Свободен").assertDoesNotExist()
            compose.onNodeWithContentDescription("Поиск станции").performClick()
            compose.onNodeWithTag("station-search-field").performTextInput("Тянь-Шань")
            compose.onNodeWithTag("station-search-field").performImeAction()
            visible("Тянь-Шань · DEMO 05")
            step("Список показывает состояния отдельных разъёмов") {
                listOf("1-port-2" to "Заряжается","2-port-1" to "Подключён","3-port-1" to "Завершён","3-port-2" to "Ошибка").forEach {(suffix,title)->
                    compose.onNodeWithTag("connector-demo-naryn-unit-$suffix").performScrollTo().performClick()
                    compose.onNodeWithTag("connector-dialog-state").assertTextEquals(title)
                    compose.onNodeWithText("Выбрать разъём").assertDoesNotExist()
                    compose.onNodeWithText("Закрыть").performClick()
                }
            }
            compose.onNodeWithTag("station-search-field").performTextClearance()
            compose.onNodeWithTag("station-search-field").performTextInput("Бишкек")
            compose.onNodeWithTag("station-search-field").performImeAction()
            visible("Демо-станций пока нет")
        }
        step("Кошелёк") {
            compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
            visible("Учебный баланс")
        }
        step("Старт и остановка симуляции") {
            compose.onNodeWithText("Зарядка",useUnmergedTree=true).performClick()
            compose.onNodeWithTag("start-charge").performScrollTo().performClick()
            visible("Зарядка идёт")
            android.os.SystemClock.sleep(1300)
            compose.onNodeWithTag("charge-power-chart").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Остановить симуляцию",substring=true).performScrollTo().performClick()
            visible("Аппарат 1 · разъём 1 · Завершён")
            compose.onNodeWithText("Освободить разъём после сеанса").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f,-10000f) }
            try {
                compose.waitUntil(5000) {
                    compose.onAllNodesWithText("Аппарат 1 · разъём 1 · Свободен").fetchSemanticsNodes().isNotEmpty()
                }
            } catch(failure:Throwable) {
                throw AssertionError(compose.onRoot().printToString(),failure)
            }
            visible("Аппарат 1 · разъём 1 · Свободен")
        }
        step("История и профиль") {
            compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
            visible("Демо-зарядка · Бишкек")
            compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
            visible("Демо-пользователь")
        }
        step("Живая геопозиция и повторное приближение к своей точке") {
            val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            fun shell(command:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes()}}
            shell("pm grant kg.qubat.ev android.permission.ACCESS_COARSE_LOCATION")
            shell("pm grant kg.qubat.ev android.permission.ACCESS_FINE_LOCATION")
            shell("appops set kg.qubat.ev android:mock_location allow")
            val manager=compose.activity.getSystemService(android.location.LocationManager::class.java)
            @Suppress("DEPRECATION")
            manager.addTestProvider(android.location.LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,android.location.Criteria.POWER_LOW,android.location.Criteria.ACCURACY_FINE)
            manager.setTestProviderEnabled(android.location.LocationManager.GPS_PROVIDER,true)
            try {
                compose.onNodeWithText("Карта",useUnmergedTree=true).performClick()
                compose.onNodeWithContentDescription("Моя позиция").performClick()
                fun fix(lat:Double,lon:Double) {
                    manager.setTestProviderLocation(android.location.LocationManager.GPS_PROVIDER,android.location.Location(android.location.LocationManager.GPS_PROVIDER).apply {
                        latitude=lat;longitude=lon;accuracy=5f;time=System.currentTimeMillis();elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
                    })
                }
                fix(42.875,74.57)
                compose.waitUntil(15000) {compose.onAllNodesWithTag("user-location").fetchSemanticsNodes().isNotEmpty()}
                var native:MapLibreMap?=null
                lateinit var view:MapView
                compose.runOnIdle {view=findMapView(compose.activity.window.decorView)!!;view.getMapAsync {native=it}}
                compose.waitUntil(10000) {native!=null && native!!.zoom>=15.4}
                fun nearMe()=compose.runOnIdle {val p=native!!.projection.fromScreenLocation(PointF(view.width/2f,view.height/2f));distanceKm(GeoPoint(p.latitude,p.longitude),GeoPoint(42.875,74.57))<.15}
                compose.waitUntil(10000) {nearMe()}
                val world=compose.onNodeWithTag("world-map")
                world.performTouchInput {swipeLeft()}
                compose.onNodeWithContentDescription("Моя позиция").performClick()
                compose.waitUntil(10000) {nearMe()}
                android.os.SystemClock.sleep(2100)
                fix(42.876,74.571)
                compose.waitUntil(10000) {compose.onNodeWithTag("user-location").fetchSemanticsNode().config[SemanticsProperties.StateDescription]=="42.876,74.571"}
            } finally {manager.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)}
        }
    }
}
