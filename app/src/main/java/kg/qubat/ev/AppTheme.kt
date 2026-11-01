package kg.qubat.ev

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

val LocalNight=staticCompositionLocalOf {true}
val LocalToggleTheme=staticCompositionLocalOf<()->Unit> {{}}
val Forest:Color @Composable get()=MaterialTheme.colorScheme.background
val SurfaceGreen:Color @Composable get()=MaterialTheme.colorScheme.surface
val Lime:Color @Composable get()=MaterialTheme.colorScheme.primary
val Muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
val Amber:Color @Composable get()=if(LocalNight.current) Color(0xFFFFCF80) else Color(0xFF895700)

@Composable fun QubatTheme(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val systemNight=isSystemInDarkTheme()
    val prefs=remember {context.getSharedPreferences("qubat_ui",0)}
    var night by remember {mutableStateOf(prefs.getBoolean("night",systemNight))}
    val colors=if(night) darkColorScheme(primary=Color(0xFF77E2B1),onPrimary=Color(0xFF052A20),background=Color(0xFF091D2B),surface=Color(0xFF132D3F),onBackground=Color(0xFFF0F7FC),onSurface=Color(0xFFF0F7FC),onSurfaceVariant=Color(0xFFA9BDC9),outline=Color(0xFF355367),secondary=Color(0xFF77E2B1),surfaceVariant=Color(0xFF1B394B))
    else lightColorScheme(primary=Color(0xFF08734E),onPrimary=Color.White,background=Color(0xFFF4F7FA),surface=Color.White,onBackground=Color(0xFF102638),onSurface=Color(0xFF102638),onSurfaceVariant=Color(0xFF566B79),outline=Color(0xFFBBCBD5),secondary=Color(0xFF08734E),surfaceVariant=Color(0xFFE6EEF3))
    val view=LocalView.current
    SideEffect {(context as? Activity)?.window?.let {window->WindowCompat.getInsetsController(window,view).apply {isAppearanceLightStatusBars=!night;isAppearanceLightNavigationBars=!night}}}
    CompositionLocalProvider(LocalNight provides night,LocalToggleTheme provides {night=!night;prefs.edit().putBoolean("night",night).apply()}) {
        MaterialTheme(colorScheme=colors,content=content)
    }
}
@Composable fun ThemeToggle() {
    val night=LocalNight.current
    val color=Lime
    val label=LocalTranslate.current(if(night) "Дневная тема" else "Ночная тема")
    TextButton(onClick=LocalToggleTheme.current,modifier=Modifier.semantics {contentDescription=label},contentPadding=androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        Canvas(Modifier.size(24.dp)) {
            if(night) {
                drawCircle(color,5.dp.toPx(),center,style=Stroke(2.dp.toPx()))
                repeat(8) {i->val angle=i*Math.PI/4;drawLine(color,Offset(center.x+cos(angle).toFloat()*8.dp.toPx(),center.y+sin(angle).toFloat()*8.dp.toPx()),Offset(center.x+cos(angle).toFloat()*11.dp.toPx(),center.y+sin(angle).toFloat()*11.dp.toPx()),2.dp.toPx())}
            } else {
                val moon=androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width*.8f,size.height*.78f)
                    cubicTo(size.width*.12f,size.height*1.04f,-size.width*.05f,size.height*.1f,size.width*.57f,size.height*.08f)
                    cubicTo(size.width*.25f,size.height*.5f,size.width*.57f,size.height*.86f,size.width*.8f,size.height*.78f)
                    close()
                }
                drawPath(moon,color)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        AppText(if(night) "Светлая" else "Тёмная",fontSize=androidx.compose.ui.unit.TextUnit.Unspecified)
    }
}
