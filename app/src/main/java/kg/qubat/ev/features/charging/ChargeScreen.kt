package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ChargeContent(station:Station,port:Connector?,state:AppState,choosing:Boolean,onChoose:(Connector)->Unit,onStart:()->Unit,onStop:(String)->Unit,onRelease:()->Unit,onStations:()->Unit,onAddVehicle:()->Unit,onCancel:()->Unit) {
    var detail by rememberSaveable{mutableStateOf<String?>(null)}
    if(state.active && !choosing) {
        val selected=if(state.charges.size==1)state.charges.single() else state.charges.firstOrNull{it.session.connectorId==detail}
        Column(Modifier.fillMaxSize().testTag("charging-viewport"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(state.charges.size>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ChargeText(if(selected!=null)"Зарядка" else "Активные зарядки",Modifier.weight(1f).testTag("charge-heading"),fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1)
                ChargePill("Демо · ${state.charges.size}")
                if(selected!=null)PressIconButton(onClick={detail=null}){AppIcon(Icons.Default.ArrowBack,"Активные зарядки")}
            }
            if(selected!=null) {
                key(selected.session.connectorId){ChargeDetail(selected,state,onStop,if(state.charges.size==1) ({detail=null;onAddVehicle()}) else null,Modifier.weight(1f))}
            } else state.charges.forEach{charge->
                key(charge.session.connectorId){ChargeSessionCard(charge,state,{detail=charge.session.connectorId},Modifier.weight(1f)){onStop(charge.session.connectorId)}}
            }
        }
        return
    }
    ChargeSelection(station,port,state,onChoose,onStart,onRelease,onStations,onCancel)
}
