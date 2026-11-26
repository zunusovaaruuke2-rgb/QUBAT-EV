@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package kg.qubat.ev

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable internal fun NativePartnerDriverDiscounts(repo:NativePartnerRepository,revision:Int,onChanged:()->Unit) {
    val context=LocalContext.current.applicationContext
    val store=remember(context){DriverDiscountStore(context)}
    val service=remember(repo,store){DriverDiscountService(repo,store)}
    val loaded=remember(repo,revision){runCatching{store.syncLocalDriver();store.load()}}
    val ledger=loaded.getOrNull()
    if(ledger==null){NativePanel("Не удалось загрузить скидки"){NetworkNote("Записи не перезаписаны. Повторите открытие раздела.",true)};return}
    if(!service.canOpen()){NativeDenied();return}
    var creating by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<DriverDiscount?>(null)}
    var delegates by rememberSaveable(repo.account.id){mutableStateOf(false)}
    var selectedMember by remember{mutableStateOf<NativeMember?>(null)}
    var query by rememberSaveable(repo.account.id){mutableStateOf("")}
    val points=service.points(ledger)
    val rules=service.rules(ledger)
    val authors=if(service.managesStaff)repo.members().associate{it.id to it.name} else emptyMap()
    LazyColumn(Modifier.fillMaxSize().testTag("network-driver-discounts"),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item("header"){
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                AppText("На ваших точках — выбранным водителям",fontSize=15.sp)
                NetworkNote("ДЕМО · поиск локального профиля по телефону. Между устройствами нужен сервер.")
                if(points.isNotEmpty())DepthButton({creating=true},Modifier.fillMaxWidth().testTag("driver-discount-create")){AppText("Добавить скидку")}
                NetworkField("Имя или номер водителя",query,"driver-discount-search"){query=it}
            }
        }
        val filtered=rules.filter{rule->val driver=ledger.drivers.firstOrNull{it.id==rule.driverId};query.isBlank() || driver?.let{it.name.contains(query,true)||it.phone.contains(query.filterNot(Char::isWhitespace))}==true}
        if(filtered.isEmpty())item("empty"){NetworkNote("Скидок пока нет.")}
        items(filtered,key={it.id}){rule->
            val driver=ledger.drivers.firstOrNull{it.id==rule.driverId}
            NativePanel(driver?.name?:"Водитель"){
                AppText("−${rule.percent}%",fontSize=22.sp,color=Lime)
                driver?.let{AppText(it.phone,fontSize=13.sp,color=Muted)}
                AppText("Назначил",fontSize=12.sp,color=Muted)
                AppText(authors[rule.authorId]?:NativePartnerRepository.accounts.firstOrNull{it.id==rule.authorId}?.name?:"Сотрудник",fontSize=13.sp)
                AppText(if(!rule.enabled)"Скидка отключена" else if(rule.endsOn?.isBefore(repo.authorizationDate)==true)"Срок истёк" else "Скидка действует",fontSize=13.sp)
                AppText(rule.pointIds.mapNotNull{id->points.firstOrNull{it.id==id}?.title}.joinToString(),fontSize=14.sp)
                AppText(rule.endsOn?.let(nativeDate::format)?:"Без срока",fontSize=13.sp,color=Muted)
                DepthOutlinedButton({editing=rule},Modifier.fillMaxWidth().testTag("driver-discount-edit-${rule.id}")){AppText("Изменить скидку")}
            }
        }
        if(service.managesStaff){
            item("delegate-open"){DepthOutlinedButton({delegates=!delegates},Modifier.fillMaxWidth().testTag("driver-discount-delegates")){AppText("Кто может выдавать скидки")}}
            if(delegates){
                item("delegate-note"){NetworkNote("Только выдача скидок. Финансы и отчёты этим разрешением не открываются.")}
                val members=repo.members().filter{it.role==PartnerRole.EMPLOYEE && !it.revoked && it.startsOn<=repo.authorizationDate && (it.endsOn==null || it.endsOn>=repo.authorizationDate)}
                if(members.isEmpty())item("no-staff"){NetworkNote("Сначала добавьте сотрудника в разделе доступа.")}
                items(members,key={"delegate-${it.id}"}){member->
                    val permission=ledger.delegations.firstOrNull{it.memberId==member.id}
                    DepthOutlinedButton({selectedMember=member},Modifier.fillMaxWidth().testTag("driver-discount-delegate-${member.id}")){
                        Column(Modifier.weight(1f)){
                            AppText(member.name,fontSize=15.sp)
                            AppText(if(permission==null)"Выдавать скидки нельзя" else "Выдача скидок разрешена",fontSize=12.sp)
                            permission?.let{AppText("До ${it.maxPercent}%",fontSize=12.sp)}
                        };AppText("›")
                    }
                }
            }
        }
    }
    if(creating || editing!=null)DriverDiscountEditor(repo,service,ledger,editing,{creating=false;editing=null}){creating=false;editing=null;onChanged()}
    selectedMember?.let{member->DiscountDelegateEditor(repo,service,ledger,member,{selectedMember=null}){selectedMember=null;onChanged()}}
}

@Composable private fun DriverDiscountEditor(repo:NativePartnerRepository,service:DriverDiscountService,ledger:DiscountLedger,
    original:DriverDiscount?,onDismiss:()->Unit,onSaved:()->Unit) {
    val points=service.points(ledger)
    var organization by remember{mutableStateOf(original?.organizationId?:points.firstOrNull()?.organizationId.orEmpty())}
    var phone by remember{mutableStateOf(ledger.drivers.firstOrNull{it.id==original?.driverId}?.phone.orEmpty())}
    var percent by remember{mutableStateOf(original?.percent?.toString()?:"")}
    var selected by remember{mutableStateOf(original?.pointIds?:emptySet())}
    var ends by remember{mutableStateOf(original?.endsOn?.toString().orEmpty())}
    var enabled by remember{mutableStateOf(original?.enabled?:true)}
    var error by remember{mutableStateOf<String?>(null)}
    val driver=discountPhone(phone)?.let{number->ledger.drivers.firstOrNull{it.phone==number}}
    NetworkEditor(if(original==null)"Добавить скидку" else "Изменить скидку","driver-discount-editor",error,onDismiss,{
        runCatching{
            val found=requireNotNull(driver){"По этому номеру водитель не найден в локальном демо-профиле"}
            val amount=requireNotNull(percent.toIntOrNull()){"Введите скидку от 1 до 99%"}
            val end=ends.takeIf(String::isNotBlank)?.let(LocalDate::parse)
            service.save(original?.id,found.id,selected,amount,end,enabled)
        }.onSuccess{onSaved()}.onFailure{error=it.message}
    }){
        NetworkField("Номер телефона водителя",phone,"driver-discount-phone"){phone=it.take(30)}
        if(driver!=null){AppText(driver.name,color=Lime);NetworkNote("Локальный демо-профиль найден")}
        else NetworkNote("В демо сначала укажите номер в профиле водителя на этом устройстве.")
        NetworkField("Размер скидки, %",percent,"driver-discount-percent",true){percent=it.take(2)}
        NetworkNote("От 1 до 99%. Несколько скидок не складываются.")
        val organizations=repo.organizations().filter{org->points.any{it.organizationId==org.id}}
        if(organizations.size>1)NetworkChoice("Организация",organization,organizations.map{it.id},{id->organizations.first{it.id==id}.name},"driver-discount-org"){organization=it;selected=emptySet()}
        AppText("На каких точках действует",fontSize=15.sp)
        points.filter{it.organizationId==organization}.forEach{point->
            DepthChip(point.id in selected,{selected=if(point.id in selected)selected-point.id else selected+point.id},Modifier.fillMaxWidth().testTag("driver-discount-point-${point.id}")){
                Column{AppText(point.title);AppText("До ${service.maxPercent(point.id,ledger)}%",fontSize=12.sp)}
            }
        }
        CalendarDateField("Действует до (необязательно)",ends,"driver-discount-until",optional=true){ends=it}
        if(ends.isBlank())NetworkNote("Без срока")
        DepthChip(enabled,{enabled=!enabled},Modifier.testTag("driver-discount-enabled")){AppText("Скидка включена")}
        NetworkNote("Цена будет проверена перед началом зарядки.")
    }
}

@Composable private fun DiscountDelegateEditor(repo:NativePartnerRepository,service:DriverDiscountService,ledger:DiscountLedger,member:NativeMember,onDismiss:()->Unit,onSaved:()->Unit) {
    val old=ledger.delegations.firstOrNull{it.memberId==member.id}
    var percent by remember{mutableStateOf(old?.maxPercent?.toString()?:"10")}
    var selected by remember{mutableStateOf(old?.pointIds?:emptySet())}
    var error by remember{mutableStateOf<String?>(null)}
    NetworkEditor("Право выдавать скидки","driver-discount-delegate-editor",error,onDismiss,{
        runCatching{service.delegate(member.id,selected,requireNotNull(percent.toIntOrNull()){"Укажите предел от 1 до 99%"});onSaved()}.onFailure{error=it.message}
    }){
        AppText(member.name)
        NetworkField("Максимальная скидка, %",percent,"driver-discount-limit",true){percent=it.take(2)}
        NetworkStationSelection(service.points(ledger).filter{it.id in member.stationIds && it.organizationId==member.organizationId},selected,"driver-discount-staff-point"){selected=it}
        NetworkNote("Это право не открывает финансы, отчёты или управление сотрудниками.")
        if(old!=null)PressTextButton({
            runCatching{service.delegate(member.id,emptySet(),null);onSaved()}.onFailure{error=it.message}
        },Modifier.testTag("driver-discount-delegate-revoke")){AppText("Запретить выдавать скидки",color=MaterialTheme.colorScheme.error)}
    }
}
