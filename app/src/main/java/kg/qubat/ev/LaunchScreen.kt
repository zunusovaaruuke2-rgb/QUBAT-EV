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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable fun QubatLaunch() {
    var finished by rememberSaveable {mutableStateOf(false)}
    Box(Modifier.fillMaxSize()) {
        QubatApp()
        if(!finished) {
            val alpha=remember {Animatable(1f)}
            val hero=remember {Animatable(0f)}
            val scale=remember {Animatable(.78f)}
            val sheen=remember {Animatable(0f)}
            LaunchedEffect(Unit) {
                delay(250)
                coroutineScope {
                    launch {hero.animateTo(1f,tween(350))}
                    launch {scale.animateTo(1f,spring(dampingRatio=.78f,stiffness=200f))}
                    launch {delay(160);sheen.animateTo(1f,tween(800,easing=LinearEasing))}
                }
                delay(100)
                alpha.animateTo(0f,tween(300))
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
                Box(Modifier.fillMaxSize().graphicsLayer {this.alpha=hero.value*.62f}.background(Color(0xFF03131E)))
                Box(Modifier.size(200.dp).align(Alignment.Center).testTag("launch-emblem")
                    .semantics {contentDescription="QUBAT EV"}
                    .graphicsLayer {scaleX=scale.value;scaleY=scale.value;this.alpha=hero.value}
                    .drawWithCache {
                        // Concentric circles and a Boolean union avoid arc end caps and seams.
                        val ring=Path().apply {
                            fillType=PathFillType.EvenOdd
                            addOval(Rect(size.width*.16f,size.height*.14f,size.width*.76f,size.height*.74f))
                            addOval(Rect(size.width*.255f,size.height*.235f,size.width*.665f,size.height*.645f))
                        }
                        val bolt=Path().apply {
                            moveTo(size.width*.45f,size.height*.51f)
                            lineTo(size.width*.58f,size.height*.51f)
                            cubicTo(size.width*.60f,size.height*.51f,size.width*.61f,size.height*.53f,size.width*.63f,size.height*.55f)
                            lineTo(size.width*.715f,size.height*.64f)
                            lineTo(size.width*.645f,size.height*.64f)
                            lineTo(size.width*.81f,size.height*.835f)
                            lineTo(size.width*.585f,size.height*.70f)
                            lineTo(size.width*.53f,size.height*.605f)
                            close()
                        }
                        val emblem=Path.combine(PathOperation.Union,ring,bolt)
                        val rim=Stroke(.8.dp.toPx())
                        val face=Brush.linearGradient(listOf(Color(0xFFE0FF91),Color(0xFF9CFA62),Color(0xFF28E7A0)),Offset(size.width*.20f,size.height*.12f),Offset(size.width*.78f,size.height*.85f))
                        val bevel=Brush.linearGradient(listOf(Color(0xFFE6FFB0),Color(0xFF44D69C)),Offset.Zero,Offset(size.width,size.height))
                        val halo=Brush.radialGradient(listOf(Color(0xFF7CFFAA).copy(alpha=.22f),Color(0xFF32DB9E).copy(alpha=.075f),Color.Transparent),center=Offset(size.width*.49f,size.height*.49f),radius=size.width*.53f)
                        val shadow=Brush.radialGradient(listOf(Color.Black.copy(alpha=.40f),Color.Transparent),center=Offset(size.width*.51f,size.height*.62f),radius=size.width*.48f)
                        onDrawBehind {
                            drawRect(shadow)
                            drawRect(halo)
                            // A few shallow vector layers give depth without bitmap blur.
                            translate(top=3.dp.toPx()) {
                                drawPath(emblem,Color(0xFF08795C))
                            }
                            translate(top=1.2.dp.toPx()) {
                                drawPath(emblem,bevel)
                            }
                            drawPath(emblem,face)
                            drawPath(emblem,bevel,style=rim)
                            // One travelling reflection uses the same unified outline.
                            val x=(-.45f+1.9f*sheen.value)*size.width
                            val reflection=Brush.linearGradient(listOf(Color.Transparent,Color.White.copy(alpha=.60f),Color.Transparent),Offset(x-size.width*.14f,0f),Offset(x+size.width*.14f,size.height*.25f))
                            drawPath(emblem,reflection)
                        }
                    })

            }
        }
    }
}
