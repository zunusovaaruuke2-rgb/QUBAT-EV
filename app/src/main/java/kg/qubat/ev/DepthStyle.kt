package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp

// Cached vector bevels: no bitmap blur, offscreen textures or animated shadows while scrolling.
@Composable fun Modifier.depthFrame(shape:Shape=RoundedCornerShape(18.dp),color:Color=SurfaceGreen,depth:Dp=3.dp,fill:Boolean=true):Modifier {
    val night=LocalNight.current
    return drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this)
        val fillBrush=Brush.linearGradient(listOf(lerp(color,Color.White,if(night).07f else .02f),color,lerp(color,Color.Black,if(night).11f else .045f)),Offset.Zero,Offset(size.width,size.height))
        val edge=Brush.linearGradient(listOf(Color.White.copy(alpha=if(night).28f else .96f),Color.White.copy(alpha=if(night).07f else .4f),Color.Black.copy(alpha=if(night).3f else .18f)),Offset.Zero,Offset(size.width,size.height))
        val rim=Stroke(1.dp.toPx())
        val offset=depth.toPx()
        onDrawWithContent {
            if(offset>0f) {
                translate(top=offset) {drawOutline(outline,Color.Black.copy(alpha=if(night).23f else .10f),style=if(fill)androidx.compose.ui.graphics.drawscope.Fill else rim)}
                translate(top=offset*.5f) {drawOutline(outline,Color.Black.copy(alpha=if(night).12f else .05f),style=if(fill)androidx.compose.ui.graphics.drawscope.Fill else rim)}
            }
            if(fill) drawOutline(outline,fillBrush)
            drawContent()
            drawOutline(outline,edge,style=rim)
        }
    }
}
@Composable fun DepthSurface(modifier:Modifier=Modifier,shape:Shape=RoundedCornerShape(18.dp),color:Color=SurfaceGreen,
    contentColor:Color=contentColorFor(color),shadowElevation:Dp=3.dp,border:BorderStroke?=null,content:@Composable ()->Unit) {
    Surface(modifier.depthFrame(shape,color,minOf(shadowElevation,4.dp)),shape,color=Color.Transparent,contentColor=contentColor,border=border,content=content)
}
@Composable fun DepthSurface(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(18.dp),color:Color=SurfaceGreen,
    contentColor:Color=contentColorFor(color),shadowElevation:Dp=3.dp,border:BorderStroke?=null,content:@Composable ()->Unit) {
    val source=remember {MutableInteractionSource()}
    Surface(onClick=onClick,modifier=Modifier.pressFeedback(source,enabled,.985f).then(modifier).depthFrame(shape,color,minOf(shadowElevation,4.dp)),enabled=enabled,shape=shape,color=Color.Transparent,contentColor=contentColor,border=border,interactionSource=source,content=content)
}
@Composable fun DepthButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(17.dp),
    colors:ButtonColors=ButtonDefaults.buttonColors(),contentPadding:PaddingValues=ButtonDefaults.ContentPadding,content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()}
    Button(onClick,Modifier.pressFeedback(source,enabled).then(modifier).depthFrame(shape,colors.containerColor,3.dp,fill=false),enabled,shape,colors,contentPadding=contentPadding,interactionSource=source,content=content)
}
@Composable fun DepthOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(17.dp),content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()}
    OutlinedButton(onClick,Modifier.pressFeedback(source,enabled).then(modifier).depthFrame(shape,SurfaceGreen,2.dp,fill=false),enabled,shape,interactionSource=source,content=content)
}
@Composable fun DepthTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()}
    FilledTonalButton(onClick,Modifier.pressFeedback(source,enabled).then(modifier).depthFrame(RoundedCornerShape(24.dp),MaterialTheme.colorScheme.secondaryContainer,2.dp,fill=false),enabled,interactionSource=source,content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DepthChip(selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,label:@Composable ()->Unit) {
    val source=remember {MutableInteractionSource()}
    FilterChip(selected,onClick,label,Modifier.pressFeedback(source).then(modifier).depthFrame(RoundedCornerShape(8.dp),if(selected)Lime.copy(alpha=.14f) else SurfaceGreen,1.dp,fill=true),interactionSource=source)
}

// Only the drawing layer animates; layout and scroll positions do not change.
@Composable fun Modifier.pressFeedback(source:InteractionSource,enabled:Boolean=true,pressedScale:Float=.96f):Modifier {
    var pressed by remember(source) {mutableStateOf(false)}
    LaunchedEffect(source,enabled) {
        pressed=false
        if(enabled) {
            var release:Job?=null
            val active=mutableSetOf<PressInteraction.Press>()
            source.interactions.collect {interaction->when(interaction) {
                is PressInteraction.Press -> {release?.cancel();active.add(interaction);pressed=true}
                is PressInteraction.Release -> {active.remove(interaction.press);if(active.isEmpty())release=launch {delay(180);pressed=false}}
                is PressInteraction.Cancel -> {active.remove(interaction.press);release?.cancel();if(active.isEmpty())pressed=false}
            }}
        }
    }
    val fraction=animateFloatAsState(if(pressed && enabled)1f else 0f,tween(if(pressed)60 else 180),label="press-compression")
    return graphicsLayer {
        val value=fraction.value
        scaleX=1f-(1f-pressedScale)*value
        scaleY=1f-(1f-pressedScale)*value
        translationY=1.5.dp.toPx()*value
    }
}
@Composable fun Modifier.pressClickable(enabled:Boolean=true,role:Role?=null,onClick:()->Unit):Modifier {
    val source=remember {MutableInteractionSource()}
    return Modifier.pressFeedback(source,enabled).then(this).clickable(interactionSource=source,indication=LocalIndication.current,enabled=enabled,role=role,onClick=onClick)
}
@Composable fun PressTextButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,contentPadding:PaddingValues=ButtonDefaults.TextButtonContentPadding,content:@Composable RowScope.()->Unit) {
    val source=remember {MutableInteractionSource()}
    TextButton(onClick,Modifier.pressFeedback(source,enabled).then(modifier),enabled,contentPadding=contentPadding,interactionSource=source,content=content)
}
@Composable fun PressIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    val source=remember {MutableInteractionSource()}
    IconButton(onClick,Modifier.pressFeedback(source,enabled).then(modifier),enabled,interactionSource=source,content=content)
}
@Composable fun PressFilledIconButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(16.dp),colors:IconButtonColors=IconButtonDefaults.filledIconButtonColors(),content:@Composable ()->Unit) {
    val source=remember {MutableInteractionSource()}
    FilledIconButton(onClick,Modifier.pressFeedback(source,enabled).then(modifier),enabled,shape,colors,interactionSource=source,content=content)
}
@Composable fun RowScope.PressNavigationBarItem(selected:Boolean,onClick:()->Unit,icon:@Composable ()->Unit,label:@Composable ()->Unit,colors:NavigationBarItemColors) {
    val source=remember {MutableInteractionSource()}
    NavigationBarItem(selected,onClick,icon,modifier=Modifier.pressFeedback(source,pressedScale=.97f),label=label,colors=colors,interactionSource=source)
}

@Composable fun PressOutlinedTextField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,singleLine:Boolean=false,
    label:(@Composable ()->Unit)?=null,placeholder:(@Composable ()->Unit)?=null,leadingIcon:(@Composable ()->Unit)?=null,
    trailingIcon:(@Composable ()->Unit)?=null,suffix:(@Composable ()->Unit)?=null,shape:Shape=RoundedCornerShape(16.dp),
    keyboardOptions:androidx.compose.foundation.text.KeyboardOptions=androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions:androidx.compose.foundation.text.KeyboardActions=androidx.compose.foundation.text.KeyboardActions.Default) {
    val source=remember {MutableInteractionSource()}
    OutlinedTextField(value,onValueChange,Modifier.pressFeedback(source,pressedScale=.99f).then(modifier),singleLine=singleLine,label=label,placeholder=placeholder,
        leadingIcon=leadingIcon,trailingIcon=trailingIcon,suffix=suffix,shape=shape,keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,interactionSource=source)
}
