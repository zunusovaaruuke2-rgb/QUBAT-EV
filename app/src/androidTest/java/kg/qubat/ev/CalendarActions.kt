package kg.qubat.ev

import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matcher
import org.junit.Assert.*
import java.time.LocalDate

internal fun assertNativeCalendar(language:String?=null) {
    onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed())).perform(object:ViewAction {
        override fun getConstraints():Matcher<View> = isAssignableFrom(DatePicker::class.java)
        override fun getDescription()="verify calendar mode and hidden keyboard"
        override fun perform(ui:UiController,view:View) {
            fun hasCalendar(v:View):Boolean = v.javaClass.simpleName.contains("DayPicker") || v is android.widget.CalendarView ||
                (v is ViewGroup && (0 until v.childCount).any{hasCalendar(v.getChildAt(it))})
            assertTrue("Date field must open a calendar",hasCalendar(view))
            assertFalse("Calendar must not show the keyboard",ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false)
            if(language!=null)assertEquals(language,view.context.resources.configuration.locales[0].language)
        }
    })
}
internal fun setCalendarDate(date:LocalDate) {
    onView(isAssignableFrom(DatePicker::class.java)).perform(object:ViewAction {
        override fun getConstraints():Matcher<View> = isAssignableFrom(DatePicker::class.java)
        override fun getDescription()="select calendar date $date"
        override fun perform(ui:UiController,view:View) {(view as DatePicker).updateDate(date.year,date.monthValue-1,date.dayOfMonth);ui.loopMainThreadUntilIdle()}
    })
}
internal fun confirmCalendarDate(date:LocalDate) {
    assertNativeCalendar()
    setCalendarDate(date)
    onView(withId(android.R.id.button1)).perform(click())
}
