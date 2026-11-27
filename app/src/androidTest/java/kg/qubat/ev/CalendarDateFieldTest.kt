package kg.qubat.ev

import android.R
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CalendarDateFieldTest {
    @get:Rule val compose=createComposeRule()
    @Test fun mandatoryDateConfirmsLeapDayAndCancelKeepsOriginal() {
        var value by mutableStateOf("2026-11-07")
        compose.setContent {QubatTheme {CompositionLocalProvider(LocalAppLanguage provides AppLanguage.RU,LocalTranslate provides {translate(it,AppLanguage.RU)}) {
            Column {CalendarDateField("С даты",value,"test-from"){value=it}}
        }}}
        compose.onNodeWithTag("test-from").performClick()
        assertNativeCalendar("ru")
        setCalendarDate(LocalDate.of(2028,2,29))
        compose.runOnIdle{assertEquals("2026-11-07",value)}
        onView(allOf(withId(R.id.button3),isDisplayed())).check(doesNotExist())
        onView(withId(R.id.button2)).perform(click())
        compose.runOnIdle{assertEquals("2026-11-07",value)}
        compose.onNodeWithTag("test-from").performClick()
        confirmCalendarDate(LocalDate.of(2028,2,29))
        compose.runOnIdle{assertEquals("2028-02-29",value)}
        compose.onNodeWithTag("test-from").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"29.02.2028"))
    }
    @Test fun optionalEndDateCanBeChosenThenClearedInEnglishAndDarkTheme() {
        var value by mutableStateOf("")
        compose.setContent {QubatTheme {CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN,LocalTranslate provides {translate(it,AppLanguage.EN)},LocalNight provides true) {
            Column {CalendarDateField("Действует до (необязательно)",value,"test-to",optional=true){value=it}}
        }}}
        compose.onNodeWithTag("test-to").performClick()
        assertNativeCalendar("en")
        confirmCalendarDate(LocalDate.of(2027,1,1))
        compose.runOnIdle{assertEquals("2027-01-01",value)}
        compose.onNodeWithTag("test-to").performClick()
        onView(withId(R.id.button3)).check(matches(withText("No date"))).perform(click())
        compose.runOnIdle{assertEquals("",value)}
        compose.onNodeWithTag("test-to").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"No date"))
    }
}
