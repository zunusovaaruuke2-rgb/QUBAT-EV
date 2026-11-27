package kg.qubat.ev

import java.util.Locale

val Ru=Locale("ru","KG")
fun money(minor: Long) = String.format(Ru,"%,.2f сом",minor/100.0)
fun energy(kwh: Double) = String.format(Ru,"%.3f",kwh)
