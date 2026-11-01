package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
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
    private fun visible(text: String) = compose.onNodeWithText(text,substring=true).performScrollTo().assertIsDisplayed()
    @Test fun mainNavigationAndDemoDisclosure() {
        step("Главная и раскрытие демо-данных") {
            compose.onNodeWithContentDescription("Фильтры карты").assertIsDisplayed().performClick()
            compose.onNodeWithText("Фильтры").assertIsDisplayed()
            compose.onNodeWithText("Показать станции · 16").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Моя позиция").assertIsDisplayed()
            val map=compose.onNodeWithTag("world-map")
            compose.waitUntil(45000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].contains("карта загружена")}
            val initial=map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].substringBefore(";")
            compose.onNodeWithContentDescription("Увеличить карту").performClick()
            compose.waitUntil(10000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription].substringBefore(";")!=initial}
            val zoomed=map.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            map.performTouchInput {swipe(start=Offset(width*.55f,height*.4f),end=Offset(width*.15f,height*.4f),durationMillis=500)}
            compose.waitUntil(10000) {map.fetchSemanticsNode().config[SemanticsProperties.StateDescription]!=zoomed}
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
