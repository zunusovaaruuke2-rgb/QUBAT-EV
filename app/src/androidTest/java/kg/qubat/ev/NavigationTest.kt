package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
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
        step("Поиск по Кыргызстану и пустое состояние") {
            compose.onNodeWithText("Список",useUnmergedTree=true).performClick()
            visible("Станции Кыргызстана")
            compose.onNodeWithText("Город или название станции").performTextInput("Нарын")
            compose.onNodeWithText("Город или название станции").performImeAction()
            visible("Тянь-Шань · DEMO 05")
            step("Список показывает состояния отдельных разъёмов") {
                listOf("1-port-2" to "Заряжается","2-port-1" to "Подключён","3-port-1" to "Завершён","3-port-2" to "Ошибка").forEach {(suffix,title)->
                    compose.onNodeWithTag("connector-demo-naryn-unit-$suffix").performScrollTo().performClick()
                    compose.onNodeWithTag("connector-dialog-state").assertTextEquals(title)
                    compose.onNodeWithText("Выбрать разъём").assertDoesNotExist()
                    compose.onNodeWithText("Закрыть").performClick()
                }
            }
            compose.onNodeWithText("Город или название станции").performTextClearance()
            compose.onNodeWithText("Город или название станции").performTextInput("Несуществующий город")
            compose.onNodeWithText("Город или название станции").performImeAction()
            visible("Демо-станций пока нет")
        }
        step("Кошелёк") {
            compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
            visible("Учебный баланс")
        }
        step("Старт и остановка симуляции") {
            compose.onNodeWithText("Зарядка",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Начать симуляцию",substring=true).performScrollTo().performClick()
            visible("Зарядка идёт")
            android.os.SystemClock.sleep(1300)
            compose.onNodeWithText("Остановить симуляцию",substring=true).performScrollTo().performClick()
            visible("Аппарат 1 · разъём 1 · Завершён")
            compose.onNodeWithText("Освободить разъём после сеанса").performScrollTo().performClick()
            visible("Аппарат 1 · разъём 1 · Свободен")
        }
        step("История и профиль") {
            compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
            visible("Демо-зарядка · Бишкек")
            compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
            visible("Демо-пользователь")
        }
    }
}
