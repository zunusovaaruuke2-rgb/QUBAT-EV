package kg.qubat.ev

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** Original supplied artwork; crop only the white outer margin at rendering time. */
@Composable fun BrandMark(size:Dp,modifier:Modifier=Modifier,description:String="QUBAT") {
    Box(modifier.size(size).clip(CircleShape),contentAlignment=Alignment.Center) {
        Image(painterResource(R.drawable.qubat_logo),description,Modifier.requiredSize(size*1.07f),contentScale=ContentScale.Fit)
    }
}
