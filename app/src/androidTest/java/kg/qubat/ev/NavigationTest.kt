package kg.qubat.ev

import androidx.compose.ui.test.*
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
            compose.onNodeWithText("Показать станции · 4").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Моя позиция").assertIsDisplayed()
            compose.onNodeWithContentDescription("Демо-станция Бишкек").performClick()
            compose.onNodeWithText("Север · DEMO 01").assertIsDisplayed()
            compose.onNodeWithContentDescription("Закрыть карточку").performClick()
        }
        step("Поиск по Кыргызстану и пустое состояние") {
            compose.onNodeWithText("Станции",useUnmergedTree=true).performClick()
            visible("Станции Кыргызстана")
            compose.onNodeWithText("Город или название станции").performTextInput("Нарын")
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
