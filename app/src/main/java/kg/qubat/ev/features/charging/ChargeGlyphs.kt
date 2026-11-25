package kg.qubat.ev

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable internal fun ConnectorGlyph(color:Color,modifier:Modifier) {
    Canvas(modifier) {
        val radius=size.minDimension*.44f
        drawCircle(color,radius,style=Stroke(2.dp.toPx()))
        drawCircle(color,radius*.74f,style=Stroke(1.dp.toPx()))
        listOf(.36f to .34f,.64f to .34f,.3f to .55f,.7f to .55f,.5f to .73f).forEach{(x,y)->
            drawCircle(color,size.minDimension*.055f,Offset(size.width*x,size.height*y))
        }
    }
}

@Composable internal fun ChargerCabinet(ports:Int,modifier:Modifier) {
    val accent=Lime
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8A9D9F),Color(0xFFF2F7F6),Color(0xFFADBABB))),Offset(w*.25f,h*.03f),Size(w*.5f,h*.94f),CornerRadius(w*.08f))
        drawRoundRect(Color(0xFF072C2D),Offset(w*.3f,h*.1f),Size(w*.4f,h*.38f),CornerRadius(w*.05f))
        drawRoundRect(accent,Offset(w*.36f,h*.16f),Size(w*.28f,h*.17f),CornerRadius(w*.02f))
        val bolt=Path().apply{moveTo(w*.54f,h*.17f);lineTo(w*.43f,h*.26f);lineTo(w*.51f,h*.26f);lineTo(w*.47f,h*.32f);lineTo(w*.58f,h*.23f);lineTo(w*.50f,h*.23f);close()};drawPath(bolt,Color(0xFF063D2C))
        drawRoundRect(accent,Offset(w*.45f,h*.57f),Size(w*.1f,h*.3f),CornerRadius(w*.03f))
        for(i in 0 until ports){val right=i%2==1;val x=if(right)w*.77f else w*.23f;val y=h*(.35f+(i/2)*.2f)
            val cable=Path().apply{moveTo(x,y);cubicTo(if(right)w*.96f else w*.04f,y,if(right)w*.96f else w*.04f,h*.84f,x,h*.72f)}
            drawPath(cable,Color(0xFF193D39),style=Stroke(w*.045f,cap=StrokeCap.Round))
            drawLine(accent,Offset(x,y),Offset(x,y+h*.13f),w*.065f,StrokeCap.Round)
        }
    }
}
@Composable internal fun CarGlyph(modifier:Modifier,color:Color=Lime) {
    Canvas(modifier) {
        val w=size.width;val h=size.height;val stroke=Stroke(w*.06f,cap=StrokeCap.Round)
        val roof=Path().apply{moveTo(w*.2f,h*.43f);lineTo(w*.3f,h*.18f);lineTo(w*.7f,h*.18f);lineTo(w*.8f,h*.43f)}
        drawPath(roof,color,style=stroke)
        drawRoundRect(color,Offset(w*.13f,h*.4f),Size(w*.74f,h*.42f),CornerRadius(w*.1f),style=stroke)
        drawLine(color,Offset(w*.23f,h*.8f),Offset(w*.23f,h*.94f),w*.09f,StrokeCap.Round);drawLine(color,Offset(w*.77f,h*.8f),Offset(w*.77f,h*.94f),w*.09f,StrokeCap.Round)
        drawLine(color,Offset(w*.22f,h*.56f),Offset(w*.31f,h*.56f),w*.06f,StrokeCap.Round);drawLine(color,Offset(w*.69f,h*.56f),Offset(w*.78f,h*.56f),w*.06f,StrokeCap.Round)
        val bolt=Path().apply{moveTo(w*.53f,h*.45f);lineTo(w*.43f,h*.62f);lineTo(w*.51f,h*.62f);lineTo(w*.47f,h*.75f);lineTo(w*.60f,h*.57f);lineTo(w*.52f,h*.57f);close()};drawPath(bolt,color)
    }
}
internal fun metricColor(kind:Int)=when(kind){0->Color(0xFF397DEB);1->Color(0xFF0891B2);2->Color(0xFF9564D8);else->Color(0xFFC28A08)}

@Composable internal fun ChargeSymbol(kind:Int,modifier:Modifier,color:Color=metricColor(kind)) {
    Canvas(modifier){val w=size.width;val h=size.height;val stroke=Stroke(w*.09f,cap=StrokeCap.Round)
        when(kind){
            0->{val bolt=Path().apply{moveTo(w*.6f,0f);lineTo(w*.15f,h*.57f);lineTo(w*.45f,h*.57f);lineTo(w*.36f,h);lineTo(w*.85f,h*.36f);lineTo(w*.57f,h*.36f);close()};drawPath(bolt,color)}
            1->{drawRoundRect(color,Offset(w*.08f,h*.2f),Size(w*.76f,h*.6f),CornerRadius(w*.1f),style=stroke);drawLine(color,Offset(w*.93f,h*.4f),Offset(w*.93f,h*.6f),w*.1f);drawLine(color,Offset(w*.45f,h*.35f),Offset(w*.35f,h*.52f),w*.09f);drawLine(color,Offset(w*.35f,h*.52f),Offset(w*.52f,h*.48f),w*.09f);drawLine(color,Offset(w*.52f,h*.48f),Offset(w*.42f,h*.65f),w*.09f)}
            2->{drawCircle(color,w*.42f,style=stroke);drawLine(color,center,Offset(w*.5f,h*.23f),w*.08f,StrokeCap.Round);drawLine(color,center,Offset(w*.7f,h*.6f),w*.08f,StrokeCap.Round)}
            else->{for(i in 0..2)drawOval(color,Offset(w*.12f,h*(.12f+i*.24f)),Size(w*.76f,h*.29f),style=stroke)}
        }
    }
}
