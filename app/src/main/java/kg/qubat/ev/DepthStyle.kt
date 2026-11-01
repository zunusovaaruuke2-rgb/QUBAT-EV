package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    Surface(onClick=onClick,modifier=modifier.depthFrame(shape,color,minOf(shadowElevation,4.dp)),enabled=enabled,shape=shape,color=Color.Transparent,contentColor=contentColor,border=border,content=content)
}
@Composable fun DepthButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(17.dp),
    colors:ButtonColors=ButtonDefaults.buttonColors(),contentPadding:PaddingValues=ButtonDefaults.ContentPadding,content:@Composable RowScope.()->Unit) {
    Button(onClick,modifier.depthFrame(shape,colors.containerColor,3.dp,fill=false),enabled,shape,colors,contentPadding=contentPadding,content=content)
}
@Composable fun DepthOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=RoundedCornerShape(17.dp),content:@Composable RowScope.()->Unit) {
    OutlinedButton(onClick,modifier.depthFrame(shape,SurfaceGreen,2.dp,fill=false),enabled,shape,content=content)
}
@Composable fun DepthTonalButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    FilledTonalButton(onClick,modifier.depthFrame(RoundedCornerShape(24.dp),MaterialTheme.colorScheme.secondaryContainer,2.dp,fill=false),enabled,content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DepthChip(selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier,label:@Composable ()->Unit) {
    FilterChip(selected,onClick,label,modifier.depthFrame(RoundedCornerShape(8.dp),if(selected)Lime.copy(alpha=.14f) else SurfaceGreen,1.dp,fill=true))
}
