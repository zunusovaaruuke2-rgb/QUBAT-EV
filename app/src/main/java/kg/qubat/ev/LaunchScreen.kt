package kg.qubat.ev

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable fun QubatLaunch() {
    var finished by rememberSaveable{mutableStateOf(false)}
    Box(Modifier.fillMaxSize()) {
        QubatApp()
        if(!finished) {
            val alpha=remember{Animatable(1f)};val hero=remember{Animatable(0f)};val scale=remember{Animatable(1f)}
            LaunchedEffect(Unit){
                delay(150)
                hero.animateTo(1f,tween(400))
                delay(650);alpha.animateTo(0f,tween(300));finished=true
            }
            val view=LocalView.current;val context=LocalContext.current;val night by rememberUpdatedState(LocalNight.current)
            DisposableEffect(Unit){
                val controller=(context as? android.app.Activity)?.window?.let{WindowCompat.getInsetsController(it,view)}
                controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false
                onDispose{controller?.isAppearanceLightStatusBars=!night;controller?.isAppearanceLightNavigationBars=!night}
            }
            Box(Modifier.fillMaxSize().testTag("launch-splash").pointerInput(Unit){awaitPointerEventScope{while(true){awaitPointerEvent().changes.forEach{it.consume()}}}}
                .graphicsLayer{this.alpha=alpha.value}.background(Brush.radialGradient(listOf(Color(0xFF07563E),Color(0xFF002C27),Color(0xFF03131E))))) {
                Box(Modifier.fillMaxSize().graphicsLayer{scaleX=scale.value;scaleY=scale.value;this.alpha=hero.value}) {
                    BrandMark(192.dp,Modifier.align(Alignment.Center).testTag("launch-emblem"),"QUBAT EV")
                    AppText("QUBAT EV",Modifier.align(Alignment.Center).offset(y=140.dp),color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp)
                }
            }
        }
    }
}
