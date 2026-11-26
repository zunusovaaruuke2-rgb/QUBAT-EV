package kg.qubat.ev

/** Presentation only: preserves the existing DEMO map's round-robin port grouping.
 * IDs/numbers stay unchanged; no new persisted fields or fabricated equipment are introduced.
 */
internal data class MonitorApparatus(val number:Int,val ports:List<NativePort>)

internal fun monitorApparatuses(station:NativeStation):List<MonitorApparatus> {
    require(station.apparatusCount>0) { "Число аппаратов должно быть положительным" }
    val groups=List(station.apparatusCount){mutableListOf<NativePort>()}
    station.ports.forEachIndexed{index,port->groups[index%station.apparatusCount].add(port)}
    return groups.mapIndexed{index,ports->MonitorApparatus(index+1,ports.toList())}
}
