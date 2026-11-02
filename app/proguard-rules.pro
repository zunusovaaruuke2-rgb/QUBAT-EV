
# Private demo snapshots use Java serialization; keep their schema stable in release APKs.
-keep class kg.qubat.ev.Native* implements java.io.Serializable { *; }
-keep class kg.qubat.ev.PartnerAccount { *; }
-keep class kg.qubat.ev.PartnerPeriod { *; }
-keep class kg.qubat.ev.PartnerRole { *; }
-keep class kg.qubat.ev.PartnerSort { *; }
-keep class kotlin.collections.EmptyList { *; }
-keep class kotlin.collections.EmptySet { *; }
-keep class kotlin.collections.EmptyMap { *; }
