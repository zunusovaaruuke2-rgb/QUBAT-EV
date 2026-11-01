package kg.qubat.ev

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun mainNavigationAndDemoDisclosure() {
        compose.onNodeWithText("Ваша энергия.\nВаша страна.").assertIsDisplayed()
        compose.onNodeWithText("Демо-данные. Станции вымышленные. Платежей нет.",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Станции",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Станции Кыргызстана").assertIsDisplayed()
        compose.onNodeWithText("Город или название станции").performTextInput("Нарын")
        compose.onNodeWithText("Демо-станций пока нет").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Учебный баланс").assertIsDisplayed()
        compose.onNodeWithText("Зарядка",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Начать симуляцию",substring=true).performScrollTo().performClick()
        compose.onNodeWithText("Зарядка идёт").performScrollTo().assertIsDisplayed()
        android.os.SystemClock.sleep(1300)
        compose.onNodeWithText("Остановить симуляцию",substring=true).performScrollTo().performClick()
        compose.onNodeWithText("Кошелёк",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Демо-зарядка · Бишкек").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Профиль",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Демо-пользователь").assertIsDisplayed()
    }
}
