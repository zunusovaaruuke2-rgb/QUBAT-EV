package kg.qubat.ev

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

@Composable fun QubatLaunch() {
    var finished by rememberSaveable {mutableStateOf(false)}
    Box(Modifier.fillMaxSize()) {
        QubatApp()
        if(!finished) {
            val alpha=remember {Animatable(1f)}
            val hero=remember {Animatable(0f)}
            val scale=remember {Animatable(.65f)}
            LaunchedEffect(Unit) {
                delay(450)
                hero.animateTo(1f,tween(350))
                scale.animateTo(1.12f,tween(450,easing=FastOutSlowInEasing))
                delay(250)
                alpha.animateTo(0f,tween(350))
                finished=true
            }
            val view=LocalView.current
            val context=LocalContext.current
            val night=LocalNight.current
            DisposableEffect(Unit) {
                val controller=(context as? android.app.Activity)?.window?.let {WindowCompat.getInsetsController(it,view)}
                controller?.isAppearanceLightStatusBars=false
                controller?.isAppearanceLightNavigationBars=false
                onDispose {controller?.isAppearanceLightStatusBars=!night;controller?.isAppearanceLightNavigationBars=!night}
            }
            Box(Modifier.fillMaxSize().testTag("launch-splash").pointerInput(Unit) {awaitPointerEventScope {while(true) {awaitPointerEvent().changes.forEach {it.consume()}}}}.graphicsLayer {this.alpha=alpha.value}.background(Color(0xFF03131E))) {
                Image(painterResource(R.drawable.launch_art),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                Box(Modifier.fillMaxSize().background(Color(0xFF03131E).copy(alpha=hero.value*.85f)))
                Canvas(Modifier.size(180.dp).align(Alignment.Center).graphicsLayer {scaleX=scale.value;scaleY=scale.value;this.alpha=hero.value}) {
                    val glow=Brush.radialGradient(listOf(Color(0xFF9FEA5C).copy(alpha=.25f),Color.Transparent))
                    drawCircle(glow)
                    val green=Brush.linearGradient(listOf(Color(0xFFD1F759),Color(0xFF41ECA0)),Offset.Zero,Offset(size.width,size.height))
                    drawArc(green,-42f,310f,false,Offset(size.width*.17f,size.height*.12f),androidx.compose.ui.geometry.Size(size.width*.65f,size.height*.65f),style=Stroke(size.width*.11f,cap=StrokeCap.Round))
                    val bolt=Path().apply {moveTo(size.width*.48f,size.height*.48f);lineTo(size.width*.68f,size.height*.48f);lineTo(size.width*.62f,size.height*.56f);lineTo(size.width*.89f,size.height*.9f);lineTo(size.width*.57f,size.height*.7f);lineTo(size.width*.65f,size.height*.68f);close()}
                    drawPath(bolt,green)
                }
            }
        }
    }
}
