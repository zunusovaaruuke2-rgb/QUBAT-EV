package kg.qubat.ev

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AccountRow(title:String,subtitle:String,mark:String,color:Color=Color(0xFF187758),tag:String="",translateSubtitle:Boolean=true,onClick:()->Unit) {
    DepthSurface(onClick=onClick,modifier=Modifier.fillMaxWidth().then(if(tag.isBlank()) Modifier else Modifier.testTag(tag)),shape=RoundedCornerShape(18.dp),color=SurfaceGreen,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.35f))) {
        Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(45.dp).depthFrame(RoundedCornerShape(13.dp),color.copy(alpha=.12f),1.dp),contentAlignment=Alignment.Center) {
                AppText(mark,color=if(LocalNight.current) Color(0xFFDCECF7) else color,fontSize=if(mark.length>3)12.sp else 20.sp,fontWeight=FontWeight.ExtraBold,fontStyle=if(mark=="VISA")FontStyle.Italic else FontStyle.Normal)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {AppText(title,fontSize=16.sp,fontWeight=FontWeight.SemiBold);if(translateSubtitle) AppText(subtitle,fontSize=11.sp,lineHeight=15.sp,color=Muted) else Text(subtitle,fontSize=11.sp,lineHeight=15.sp,color=Muted)}
            AppIcon(Icons.Default.ArrowForward,null,tint=Muted,modifier=Modifier.size(18.dp))
        }
    }
}
