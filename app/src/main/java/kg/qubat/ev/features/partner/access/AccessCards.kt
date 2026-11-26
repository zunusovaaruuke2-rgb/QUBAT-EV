@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

internal fun memberAccessStatus(member: NativeMember, today: LocalDate, permissions: Set<NativePermission>, hasPoints: Boolean): String = when {
    member.revoked -> "Отключён"
    member.endsOn?.isBefore(today) == true -> "Срок истёк"
    member.startsOn.isAfter(today) -> "Ещё не начался"
    !hasPoints -> "Нет назначенных точек"
    NativePermission.VIEW !in permissions -> "Без права просмотра"
    else -> "Доступ открыт"
}

internal fun accessRoleLabel(role: PartnerRole): String = if (role == PartnerRole.EMPLOYEE) "Сотрудник" else role.label

@Composable internal fun AccessPermissionSummary(permissions: Set<NativePermission>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (NativePermission.VIEW in permissions) AppText("Просмотр станций", fontSize = 13.sp)
        if (NativePermission.REPORT in permissions) AppText("Просмотр отчётов", fontSize = 13.sp)
        if (NativePermission.FINANCE in permissions) AppText("Просмотр финансов", fontSize = 13.sp)
        if (NativePermission.FINANCE !in permissions && NativePermission.EDIT_FINANCE !in permissions)
            AppText("Без доступа к финансам", fontSize = 13.sp, color = Muted)
        if (NativePermission.EDIT_FINANCE in permissions) AppText("Может изменять финансовые записи", fontSize = 13.sp)
        AppText(if (NativePermission.EDIT_TARIFF in permissions) "Может менять тарифы" else "Без изменения тарифов", fontSize = 13.sp, color = Muted)
    }
}

@Composable internal fun AccessMemberCard(member: NativeMember, points: List<NativeStation>, permissions: Set<NativePermission>,
    today: LocalDate, onOpen: () -> Unit) {
    val assigned = points.filter { it.id in member.stationIds && it.organizationId == member.organizationId }
    val status = memberAccessStatus(member, today, permissions, assigned.isNotEmpty())
    NetworkCard(member.name, Modifier.testTag("network-member-${member.id}")) {
        AppText(status, Modifier.testTag("network-member-status-${member.id}"),
            fontSize = 13.sp, color = if (status == "Доступ открыт") Lime else Muted)
        AppText(accessRoleLabel(member.role), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        AppText("Доступные точки", fontSize = 12.sp, color = Muted)
        AppText(assigned.joinToString { it.title }.ifBlank { "Нет назначенных точек" }, fontSize = 14.sp)
        if (status == "Доступ открыт") AccessPermissionSummary(permissions)
        else AppText("Сейчас не имеет доступа", fontSize = 13.sp, color = Muted)
        if (member.startsOn > today) {
            AppText("Начало доступа", fontSize = 12.sp, color = Muted)
            AppText(nativeDate.format(member.startsOn), fontSize = 13.sp)
        }
        AppText("Срок доступа", fontSize = 12.sp, color = Muted)
        AppText(member.endsOn?.let(nativeDate::format) ?: "Без ограничения", fontSize = 13.sp)
        DepthOutlinedButton(onOpen, Modifier.fillMaxWidth().testTag("network-member-open-${member.id}")) {
            AppText(if (member.revoked) "Посмотреть" else "Настроить доступ", fontSize = 14.sp)
        }
    }
}

@Composable internal fun AccessMemberDetails(member: NativeMember, points: List<NativeStation>, permissions: Set<NativePermission>,
    today: LocalDate, onDismiss: () -> Unit, onEdit: () -> Unit, onRevoke: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { AppText(member.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText(accessRoleLabel(member.role), fontWeight = FontWeight.SemiBold)
            AppText(member.contact, fontSize = 14.sp)
            val assigned = points.filter { it.id in member.stationIds && it.organizationId == member.organizationId }
            AppText(memberAccessStatus(member, today, permissions, assigned.isNotEmpty()), color = Muted)
            AppText("Доступные точки", fontWeight = FontWeight.SemiBold)
            assigned.forEach { AppText(it.title, fontSize = 14.sp) }
            if (assigned.isEmpty()) AppText("Нет назначенных точек", fontSize = 14.sp)
            AppText("Начало доступа", color = Muted, fontSize = 12.sp)
            AppText(nativeDate.format(member.startsOn), fontSize = 14.sp)
            AppText("Окончание доступа", color = Muted, fontSize = 12.sp)
            AppText(member.endsOn?.let(nativeDate::format) ?: "Без ограничения", fontSize = 14.sp)
            AppText("Разрешения по настройкам", fontWeight = FontWeight.SemiBold)
            permissions.forEach { AppText(networkPermissionLabel(it), fontSize = 14.sp) }
            if (permissions.isEmpty()) AppText("Нет разрешений")
            if (!member.revoked) {
                DepthOutlinedButton(onEdit, Modifier.fillMaxWidth().testTag("network-member-edit-${member.id}")) { AppText("Изменить роль и точки") }
                PressTextButton(onRevoke, Modifier.fillMaxWidth().testTag("network-member-revoke-${member.id}")) {
                    AppText("Отключить доступ", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }, confirmButton = { PressTextButton(onDismiss) { AppText("Закрыть") } })
}
