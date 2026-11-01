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
    private fun visible(text: String) = compose.onNodeWithText(text,substring=true).performScrollTo().assertIsDisplayed()
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
            compose.runOnIdle {findMapView(compose.activity.window.decorView)!!.getMapAsync {nativeMap=it}}
            compose.waitUntil(10000) {nativeMap!=null}
            fun camera()=compose.runOnIdle {nativeMap!!.cameraPosition}
            val initial=camera().zoom
            step("Увеличение масштаба карты") {
                compose.onNodeWithContentDescription("Увеличить карту").performClick()
                try {compose.waitUntil(10000) {camera().zoom>initial+0.5}}
                catch(failure:Throwable) {throw AssertionError("Масштаб до: $initial; после: ${camera().zoom}; ${map.fetchSemanticsNode().config[SemanticsProperties.StateDescription]}",failure)}
            }
            val zoomed=camera().target!!
            step("Перемещение нативной карты") {
                onView(isAssignableFrom(MapView::class.java)).perform(GeneralSwipeAction(Swipe.SLOW,GeneralLocation.CENTER,GeneralLocation.CENTER_LEFT,Press.FINGER))
                try {compose.waitUntil(10000) {val target=camera().target!!;kotlin.math.abs(target.longitude-zoomed.longitude)>0.001}}
                catch(failure:Throwable) {throw AssertionError("Центр до: $zoomed; после: ${camera().target}",failure)}
            }
            compose.onNodeWithContentDescription("Уменьшить карту").performClick()
        }
        step("Поиск по Кыргызстану и пустое состояние") {
            compose.onNodeWithText("Станции",useUnmergedTree=true).performClick()
            visible("Станции Кыргызстана")
            compose.onNodeWithText("Город или название станции").performTextInput("Нарын")
            compose.onNodeWithText("Город или название станции").performImeAction()
            visible("Тянь-Шань · DEMO 05")
            compose.onNodeWithText("Город или название станции").performScrollTo().performTextClearance()
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
        }
        step("История и профиль") {
            compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
            visible("Демо-зарядка · Бишкек")
            compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
            visible("Демо-пользователь")
        }
    }
}
