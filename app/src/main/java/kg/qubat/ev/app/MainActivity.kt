package kg.qubat.ev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Android entry point. Navigation lives in DriverApp; screens live in features/. */
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { QubatTheme { AppLanguageProvider { QubatLaunch() } } } }
}
