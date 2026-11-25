package kg.qubat.ev

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Keep the window alive until its exit animation finishes, including Back/outside taps. */
@Composable fun ApparatusDialog(station:Station,unit:Charger,onDismiss:()->Unit,onConnector:(Connector)->Unit) {
    val progress=remember {Animatable(0f)}
    var closing by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<Connector?>(null)}
    val dismiss by rememberUpdatedState(onDismiss)
    val choose by rememberUpdatedState(onConnector)
    val close:(Connector?)->Unit={port->if(!closing) {selected=port;closing=true}}
    LaunchedEffect(closing) {
        progress.animateTo(if(closing)0f else 1f,tween(if(closing)170 else 300,easing=FastOutSlowInEasing))
        if(closing) selected?.let {choose(it)} ?: dismiss()
    }
    Dialog(onDismissRequest={close(null)},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // The Compose scrim fades with the card instead of appearing in one frame.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
        }
        val scrim=remember {MutableInteractionSource()}
        val title=LocalTranslate.current("Аппарат ${unit.number}")
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer {alpha=progress.value}
                .background(Color.Black.copy(alpha=.48f))
                .clickable(interactionSource=scrim,indication=null,onClick={close(null)}))
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),contentAlignment=Alignment.Center) {
                DepthSurface(modifier=Modifier.widthIn(max=520.dp).fillMaxWidth().heightIn(max=maxHeight)
                    .graphicsLayer {
                        val value=progress.value
                        alpha=value
                        scaleX=.92f+.08f*value
                        scaleY=.92f+.08f*value
                        translationY=24.dp.toPx()*(1f-value)
                    }.testTag("apparatus-dialog").semantics {paneTitle=title},
                    shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        AppText("Аппарат ${unit.number}",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                        AppText("${station.title} · ${unit.powerKw} кВт",fontSize=13.sp,color=Muted)
                        unit.connectors.forEach {port->
                            DepthSurface(onClick={close(port)},enabled=!closing,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Box(Modifier.width(56.dp)) {ConnectorBadge(unit,port) {close(port)}}
                                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        AppText("GB/T · разъём ${port.number}",fontWeight=FontWeight.Bold,fontSize=14.sp)
                                        AppText(port.state.title+(port.percent?.let {" · $it%"} ?: ""),fontSize=12.sp,color=Muted)
                                    }
                                }
                            }
                        }
                        PressTextButton(onClick={close(null)},enabled=!closing,modifier=Modifier.align(Alignment.End)) {AppText("Закрыть")}
                    }
                }
            }
        }
    }
}
