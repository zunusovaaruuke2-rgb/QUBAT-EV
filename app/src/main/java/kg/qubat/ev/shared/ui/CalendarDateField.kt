package kg.qubat.ev

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val calendarDateFormat=DateTimeFormatter.ofPattern("dd.MM.yyyy")
private fun Context.calendarActivity():Activity?=when(this) {
    is Activity->this
    is ContextWrapper->baseContext.takeIf{it!==this}?.calendarActivity()
    else->null
}

/** ISO dates stay in the model; only the user-facing format changes. Never opens the IME. */
@Composable fun CalendarDateField(label:String,value:String,tag:String,optional:Boolean=false,onChange:(String)->Unit) {
    val context=LocalContext.current
    val language=LocalAppLanguage.current
    val night=LocalNight.current
    val localize=LocalTranslate.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val change by rememberUpdatedState(onChange)
    val parsed=remember(value){runCatching{LocalDate.parse(value)}.getOrNull()}
    val display=remember(parsed){parsed?.format(calendarDateFormat).orEmpty()}
    var picker by remember{mutableStateOf<DatePickerDialog?>(null)}
    DisposableEffect(context){onDispose{picker?.dismiss()}}
    val open={
        if(picker==null) {
            focus.clearFocus(force=true);keyboard?.hide()
            val locale=Locale.forLanguageTag(language.code)
            val configuration=Configuration(context.resources.configuration).apply{setLocale(locale);setLayoutDirection(locale)}
            val theme=if(night)R.style.Theme_Qubat_Calendar_Dark else R.style.Theme_Qubat_Calendar_Light
            val themed=ContextThemeWrapper(context,theme).apply{applyOverrideConfiguration(configuration)}
            val initial=parsed ?: LocalDate.now(PARTNER_ZONE)
            val dialog=DatePickerDialog(themed,theme,{_,year,month,day->change(LocalDate.of(year,month+1,day).toString())},initial.year,initial.monthValue-1,initial.dayOfMonth)
            dialog.setTitle(localize(label))
            dialog.setButton(DatePickerDialog.BUTTON_POSITIVE,themed.getString(android.R.string.ok),dialog)
            dialog.setButton(DatePickerDialog.BUTTON_NEGATIVE,themed.getString(android.R.string.cancel)){_,_->}
            if(optional)dialog.setButton(DatePickerDialog.BUTTON_NEUTRAL,localize("Без даты")){_,_->change("")}
            context.calendarActivity()?.let{dialog.setOwnerActivity(it)}
            dialog.setOnDismissListener{picker=null}
            picker=dialog
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            dialog.show()
        }
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(display,{},modifier=Modifier.fillMaxWidth().clearAndSetSemantics{},readOnly=true,singleLine=true,
            label={AppText(label)},placeholder={AppText("Без даты")},trailingIcon={AppIcon(Icons.Default.DateRange,null)})
        // An overlay catches the whole field, including the icon, before text selection/focus.
        Box(Modifier.matchParentSize().clickable(role=Role.Button,onClick=open).testTag(tag).semantics {
            contentDescription=localize(label)
            stateDescription=if(display.isEmpty())localize("Без даты") else display
        })
    }
}
