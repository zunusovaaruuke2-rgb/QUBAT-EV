package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun MapCredits() {
    var expanded by remember {mutableStateOf(false)}
    Surface(shape=CircleShape,color=SurfaceGreen.copy(alpha=.9f),shadowElevation=2.dp) {
        TextButton(onClick={expanded=true},modifier=Modifier.size(36.dp).testTag("map-credits").appDescription("Источники карты"),contentPadding=PaddingValues(0.dp)) {AppText("©",fontSize=18.sp,color=Muted)}
    }
    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={AppText("Источники карты")},text={
        Text(buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright")) {append("© OpenStreetMap contributors")}
            append("\n\n")
            withLink(LinkAnnotation.Url("https://openmaptiles.org/")) {append("© OpenMapTiles")}
            append("\n\n")
            withLink(LinkAnnotation.Url("https://openfreemap.org/")) {append("OpenFreeMap")}
        },modifier=Modifier.verticalScroll(rememberScrollState()))
    },confirmButton={TextButton(onClick={expanded=false}) {AppText("Закрыть")}})
}
