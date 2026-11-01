package kg.qubat.ev

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

enum class AppLanguage(val code:String,val nativeName:String) { RU("ru","Русский"),KY("ky","Кыргызча"),EN("en","English") }
val LocalAppLanguage=staticCompositionLocalOf {AppLanguage.RU}
val LocalSetLanguage=staticCompositionLocalOf<(AppLanguage)->Unit> {{}}
val LocalTranslate=staticCompositionLocalOf<(String)->String> {{it}}

@Composable fun AppLanguageProvider(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val prefs=remember {context.getSharedPreferences("qubat_ui",0)}
    var language by remember {mutableStateOf(AppLanguage.entries.firstOrNull {it.code==prefs.getString("language","ru")} ?: AppLanguage.RU)}
    val translator=remember(language) {{text:String->translate(text,language)}}
    CompositionLocalProvider(LocalAppLanguage provides language,LocalTranslate provides translator,LocalSetLanguage provides {language=it;prefs.edit().putString("language",it.code).apply()},content=content)
}

@Composable fun AppText(text:String,modifier:Modifier=Modifier,color:Color=Color.Unspecified,fontSize:TextUnit=TextUnit.Unspecified,
    fontStyle:FontStyle?=null,fontWeight:FontWeight?=null,letterSpacing:TextUnit=TextUnit.Unspecified,
    textAlign:TextAlign?=null,lineHeight:TextUnit=TextUnit.Unspecified,maxLines:Int=Int.MAX_VALUE) {
    Text(LocalTranslate.current(text),modifier=modifier,color=color,fontSize=fontSize,fontStyle=fontStyle,fontWeight=fontWeight,letterSpacing=letterSpacing,textAlign=textAlign,lineHeight=lineHeight,maxLines=maxLines)
}
@Composable fun AppIcon(imageVector:ImageVector,contentDescription:String?,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val localize=LocalTranslate.current
    Icon(imageVector,contentDescription?.let(localize),modifier,tint)
}
@Composable fun Modifier.appDescription(text:String):Modifier {
    val description=LocalTranslate.current(text)
    return semantics {contentDescription=description}
}
@Composable fun LanguagePicker(onDismiss:()->Unit) {
    val selected=LocalAppLanguage.current
    val change=LocalSetLanguage.current
    AlertDialog(onDismissRequest=onDismiss,title={AppText("Язык")},text={
        androidx.compose.foundation.layout.Column {
            AppLanguage.entries.forEach {language->TextButton(onClick={change(language);onDismiss()}) {
                RadioButton(selected=selected==language,onClick=null)
                Text(language.nativeName)
            }}
        }
    },confirmButton={TextButton(onClick=onDismiss) {AppText("Закрыть")}})
}

private data class Translation(val ru:String,val en:String,val ky:String) {
    val pattern=Regex("(?<![\\p{L}])"+Regex.escape(ru)+"(?![\\p{L}])")
}
internal fun translate(text:String,language:AppLanguage):String {
    if(language==AppLanguage.RU || text.isBlank() || text.none {it in 'А'..'я' || it=='ё' || it=='Ё'}) return text
    val key=text.trim()
    val exact=translationByKey[key]
    if(exact!=null) return text.replace(key,if(language==AppLanguage.EN) exact.en else exact.ky)
    val available=Regex("^Свободно (\\d+) из (\\d+) разъёмов · (.+)$").matchEntire(text)
    if(available!=null) {
        val (free,total,tail)=available.destructured
        return if(language==AppLanguage.EN) "$free of $total connectors available · ${translate(tail,language)}" else "$total туташтыргыч · $free бош · ${translate(tail,language)}"
    }
    var result=text
    translationsByLength.forEach {entry->result=entry.pattern.replace(result) {if(language==AppLanguage.EN)entry.en else entry.ky}}
    return result
}

private val translations=listOf(
    Translation("Карта","Map","Карта"),
    Translation("Список","List","Тизме"),
    Translation("Зарядка","Charging","Кубаттоо"),
    Translation("Кошелёк","Wallet","Капчык"),
    Translation("Профиль","Profile","Профиль"),
    Translation("Язык","Language","Тил"),
    Translation("Русский","Russian","Орусча"),
    Translation("Дневная тема","Light mode","Күндүзгү режим"),
    Translation("Ночная тема","Dark mode","Түнкү режим"),
    Translation("Тема","Theme","Тема"),
    Translation("Светлая","Light","Жарык"),
    Translation("Тёмная","Dark","Караңгы"),
    Translation("Название станции","Station name","Станциянын аталышы"),
    Translation("Поиск станции","Search stations","Станция издөө"),
    Translation("Закрыть поиск","Close search","Издөөнү жабуу"),
    Translation("Станция не найдена","Station not found","Станция табылган жок"),
    Translation("Фильтры","Filters","Чыпкалар"),
    Translation("Фильтры карты","Map filters","Картанын чыпкалары"),
    Translation("Фильтры списка","List filters","Тизменин чыпкалары"),
    Translation("Мощность аппарата","Charger power","Түзмөктүн кубаттуулугу"),
    Translation("Любая","Any","Каалаган"),
    Translation("Доступность локации","Station availability","Станциянын жеткиликтүүлүгү"),
    Translation("Разъёмы · GB/T DC","Connectors · GB/T DC","Туташтыргычтар · GB/T DC"),
    Translation("Показать станции","Show stations","Станцияларды көрсөтүү"),
    Translation("Сбросить фильтры","Reset filters","Чыпкаларды тазалоо"),
    Translation("Все","All","Баары"),
    Translation("Свободны","Available","Бош"),
    Translation("Свободен","Available","Бош"),
    Translation("Свободна","Available","Бош"),
    Translation("Занята","Busy","Бош эмес"),
    Translation("Заряжается","Charging","Кубатталып жатат"),
    Translation("Завершён","Completed","Аяктады"),
    Translation("Ошибка","Error","Ката"),
    Translation("Подключён","Connected","Туташкан"),
    Translation("Недоступен","Unavailable","Жеткиликсиз"),
    Translation("Недоступна","Unavailable","Жеткиликсиз"),
    Translation("Закрыть","Close","Жабуу"),
    Translation("Закрыть карточку","Close station card","Станциянын карточкасын жабуу"),
    Translation("Маршрут и детали","Route and details","Багыт жана маалымат"),
    Translation("Выбрать станцию","Choose a station","Станцияны тандоо"),
    Translation("Выбрать другую станцию","Choose another station","Башка станцияны тандоо"),
    Translation("Увеличить карту","Zoom in","Картаны чоңойтуу"),
    Translation("Уменьшить карту","Zoom out","Картаны кичирейтүү"),
    Translation("Обзор Кыргызстана","Country overview","Кыргызстанды кароо"),
    Translation("Моя позиция","My location","Менин жайгашкан жерим"),
    Translation("Вы здесь","You are here","Сиз бул жердесиз"),
    Translation("Настройки геопозиции","Location settings","Жайгашкан жердин жөндөөлөрү"),
    Translation("Местоположение не определено","Location unknown","Жайгашкан жер аныкталган жок"),
    Translation("Определяем ваше местоположение…","Finding your location…","Жайгашкан жериңиз аныкталууда…"),
    Translation("Включите геолокацию в настройках телефона","Enable location in your phone settings","Телефондун жөндөөлөрүнөн жайгашкан жерди күйгүзүңүз"),
    Translation("Разрешите доступ к геопозиции в настройках приложения","Allow location access in app settings","Колдонмонун жөндөөлөрүнөн жайгашкан жерге уруксат бериңиз"),
    Translation("Разрешите доступ к геопозиции в настройках приложения.","Allow location access in app settings.","Колдонмонун жөндөөлөрүнөн жайгашкан жерге уруксат бериңиз."),
    Translation("Нет сигнала GPS. Выйдите на открытое место и повторите.","No GPS signal. Move outdoors and try again.","GPS сигналы жок. Ачык жерге чыгып, кайра аракет кылыңыз."),
    Translation("Служба геопозиции недоступна. Попробуйте ещё раз","Location service unavailable. Try again","Жайгашкан жер кызматы жеткиликсиз. Кайра аракет кылыңыз"),
    Translation("Приблизительное положение · включите точную геопозицию","Approximate location · enable precise location","Болжолдуу жайгашкан жер · так жайгашкан жерди күйгүзүңүз"),
    Translation("Вы на карте · точность","Your location · accuracy","Сиз картадасыз · тактыгы"),
    Translation("Карта мира","World map","Дүйнө картасы"),
    Translation("Карта загружается","Loading map","Карта жүктөлүүдө"),
    Translation("Карта недоступна · проверьте интернет","Map unavailable · check internet","Карта жеткиликсиз · интернетти текшериңиз"),
    Translation("Загрузка карты · нужен интернет","Loading map · internet required","Карта жүктөлүүдө · интернет керек"),
    Translation("Загрузка карты… Проверьте сеть и ключ","Loading map… Check connection and API key","Карта жүктөлүүдө… Интернетти жана API ачкычын текшериңиз"),
    Translation("Демо-станций пока нет","No demo stations found","Демо-станциялар табылган жок"),
    Translation("С такими фильтрами учебных точек нет.","No demo stations match these filters.","Бул чыпкаларга туура келген демо-станциялар жок."),
    Translation("Нет демо-станций по выбранным фильтрам","No demo stations match these filters","Бул чыпкаларга туура келген демо-станциялар жок"),
    Translation("Источники карты","Map credits","Картанын булактары"),
    Translation("Начать зарядку","Start charging","Кубаттоону баштоо"),
    Translation("Зарядка идёт","Charging in progress","Кубаттоо жүрүп жатат"),
    Translation("Выберите разъём и начните зарядку","Select a connector and start charging","Туташтыргычты тандап, кубаттоону баштаңыз"),
    Translation("Выберите аппарат","Select charger","Түзмөктү тандаңыз"),
    Translation("Выберите разъём","Select connector","Туташтыргычты тандаңыз"),
    Translation("Готово к зарядке","Ready to charge","Кубаттоого даяр"),
    Translation("Автомобиль заряжается","Vehicle is charging","Унаа кубатталып жатат"),
    Translation("Сеанс завершён","Session completed","Сеанс аяктады"),
    Translation("Разъём занят или недоступен","Connector busy or unavailable","Туташтыргыч бош эмес же жеткиликсиз"),
    Translation("Подключите автомобиль и выберите свободный разъём.","Connect your vehicle and choose a free connector.","Унааны туташтырып, бош туташтыргычты тандаңыз."),
    Translation("Заряд батареи · демо","Battery charge · demo","Батареянын заряды · демо"),
    Translation("Макс. мощность","Maximum power","Эң жогорку кубаттуулук"),
    Translation("Тариф","Rate","Тариф"),
    Translation("Разъём","Connector","Туташтыргыч"),
    Translation("Стоимость","Cost","Баасы"),
    Translation("Расчёт по фактически полученной энергии · демо","Based on energy delivered · demo","Берилген энергия боюнча эсептелет · демо"),
    Translation("Время","Time","Убакыт"),
    Translation("Получено","Delivered","Берилди"),
    Translation("Сейчас","Now","Азыр"),
    Translation("Остановить симуляцию","Stop simulation","Симуляцияны токтотуу"),
    Translation("Учебный режим · начать симуляцию без реального подключения","Demo mode · simulation without a real connection","Демо-режим · чыныгы туташуусуз симуляция"),
    Translation("Освободить разъём после сеанса","Release connector after session","Сеанстан кийин туташтыргычты бошотуу"),
    Translation("Освободить разъём","Release connector","Туташтыргычты бошотуу"),
    Translation("Мощность по уровню заряда","Power by battery charge","Заряд деңгээли боюнча кубаттуулук"),
    Translation("График появится после начала зарядки","The chart appears after charging starts","График кубаттоо башталгандан кийин чыгат"),
    Translation("Вертикаль — кВт · горизонталь — заряд батареи","Vertical: kW · horizontal: battery charge","Тик огу: кВт · туурасынан: батареянын заряды"),
    Translation("Посмотреть мощность при выбранном проценте","Inspect power at selected charge level","Тандалган заряд деңгээлиндеги кубаттуулукту көрүү"),
    Translation("График мощности, пока нет данных","Power chart, no data yet","Кубаттуулук графиги, азырынча маалымат жок"),
    Translation("ДЕМО · батарея 60 кВт·ч, начальный заряд 20%. Мощность модели — 80 кВт. График показывает прошедший сеанс; данные автомобиля и станции не подключены.","DEMO · 60 kWh battery, initial charge 20%. Model power: 80 kW. The chart shows the elapsed session; vehicle and station data are not connected.","ДЕМО · батарея 60 кВт·саат, баштапкы заряд 20%. Моделдин кубаттуулугу — 80 кВт. График өткөн сеансты көрсөтөт; унаа менен станциянын чыныгы маалыматтары туташкан эмес."),
    Translation("Автомобиль уже подключён и ожидает старта. Разъём занят.","A vehicle is connected and waiting to start. Connector occupied.","Унаа туташып, башталышын күтүп жатат. Туташтыргыч бош эмес."),
    Translation("Идёт демонстрационная зарядка. Разъём занят.","Demo charging is in progress. Connector occupied.","Демо-кубаттоо жүрүп жатат. Туташтыргыч бош эмес."),
    Translation("Сеанс завершён. Разъём ещё не освобождён.","Session completed. The connector has not been released yet.","Сеанс аяктады. Туташтыргыч азырынча бошотулган жок."),
    Translation("Демонстрационная ошибка разъёма. Запуск недоступен.","Demo connector error. Charging cannot start.","Туташтыргычтын демо-катасы. Кубаттоону баштоо мүмкүн эмес."),
    Translation("Разъём недоступен.","Connector unavailable.","Туташтыргыч жеткиликсиз."),
    Translation("Разъём свободен. Можно выбрать для учебной зарядки.","Connector available for demo charging.","Туташтыргыч демо-кубаттоо үчүн бош."),
    Translation("Демо-сеанс завершён. Запись добавлена в кошелёк.","Demo session completed. Added to wallet history.","Демо-сеанс аяктады. Капчыктын тарыхына кошулду."),
    Translation("Запущена симуляция. Реальная станция не подключена.","Simulation started. No real station is connected.","Симуляция башталды. Чыныгы станция туташкан эмес."),
    Translation("Станция недоступна или недостаточно учебного баланса","Station unavailable or insufficient demo balance","Станция жеткиликсиз же демо-баланс жетишсиз"),
    Translation("К зарядке","Go to charging","Кубаттоого өтүү"),
    Translation("Вымышленная станция у центра города. По этим координатам зарядной станции может не быть. Маршрут — только для проверки интерфейса.","Fictional station near the city centre. There may be no charger at these coordinates. Routing is for interface testing only.","Шаардын борборуна жакын ойдон чыгарылган станция. Бул координаттарда чыныгы станция болбошу мүмкүн. Багыт интерфейсти текшерүү үчүн гана."),
    Translation("Учебный маршрут · 2ГИС","Demo route · 2GIS","Демо-багыт · 2GIS"),
    Translation("Открыть маршрут в Google Maps","Open route in Google Maps","Багытты Google Maps'тан ачуу"),
    Translation("Установите 2ГИС или выберите Google Maps","Install 2GIS or choose Google Maps","2GIS орнотуңуз же Google Maps тандаңыз"),
    Translation("Не найдено приложение для маршрута","No navigation app found","Багыт көрсөтүүчү колдонмо табылган жок"),
    Translation("Учебный баланс","Demo balance","Демо-баланс"),
    Translation("Учебные средства","Demo funds","Демо-каражаттар"),
    Translation("История баланса","Balance history","Баланстын тарыхы"),
    Translation("История операций","Transaction history","Операциялардын тарыхы"),
    Translation("Назад к кошельку","Back to wallet","Капчыкка кайтуу"),
    Translation("Способы пополнения","Top-up methods","Толуктоо ыкмалары"),
    Translation("Демо-режим · банковские списания отключены","Demo mode · bank charges disabled","Демо-режим · банктан акча алынбайт"),
    Translation("Банковская карта","Bank card","Банк картасы"),
    Translation("Электронный кошелёк","E-wallet","Электрондук капчык"),
    Translation("Национальная платёжная система","National payment system","Улуттук төлөм системасы"),
    Translation("Оплата по QR","QR payment","QR аркылуу төлөө"),
    Translation("Интернет-банкинг","Internet banking","Интернет-банкинг"),
    Translation("Интернет-банкинг и кошельки","Internet banking and wallets","Интернет-банкинг жана капчыктар"),
    Translation("Пополнение через приложение банка","Top up through the bank app","Банк колдонмосу аркылуу толуктоо"),
    Translation("Промокод","Promo code","Промокод"),
    Translation("Подарочные коды и акции","Gift codes and promotions","Белек коддору жана акциялар"),
    Translation("Платёжный терминал","Payment terminal","Төлөм терминалы"),
    Translation("Пополнение наличными","Cash top-up","Нак акча менен толуктоо"),
    Translation("Промокоды будут доступны после подключения сервиса акций. Сейчас деньги не начисляются по кодам.","Promo codes will be available when the promotions service is connected. Codes do not currently add funds.","Акциялар кызматы туташкандан кийин промокоддор жеткиликтүү болот. Азыр коддор боюнча каражат кошулбайт."),
    Translation("Терминальное пополнение пока не подключено. Идентификатор для настоящего платежа не выдаётся. Для проверки интерфейса выберите способ демо-пополнения выше.","Terminal payments are not connected. No real payment ID is issued. Choose a demo top-up method above to test the interface.","Терминал аркылуу толуктоо азырынча туташкан эмес. Чыныгы төлөм үчүн идентификатор берилбейт. Интерфейсти текшерүү үчүн жогорудагы демо-толуктоо ыкмасын тандаңыз."),
    Translation("Здесь появятся пополнения и завершённые зарядки","Top-ups and completed charges appear here","Толуктоолор жана аяктаган кубаттоолор бул жерде көрсөтүлөт"),
    Translation("Демо-пополнение","Demo top-up","Демо-толуктоо"),
    Translation("Это проверка интерфейса. Карта и приложение банка не используются; средства будут учебными.","Interface test only. Your card and banking app are not used; funds are for demo use.","Бул интерфейсти текшерүү гана. Картаңыз жана банк колдонмосу колдонулбайт; каражаттар демо үчүн."),
    Translation("Сумма в сомах","Amount in som","Сом менен сумма"),
    Translation("Пополнение доступно после завершения сеанса","Top-up is available after the session ends","Толуктоо сеанс аяктагандан кийин жеткиликтүү"),
    Translation("Пополнение сейчас недоступно","Top-up is currently unavailable","Толуктоо азыр жеткиликсиз"),
    Translation("Пополнить демо-баланс","Top up demo balance","Демо-балансты толуктоо"),
    Translation("Понятно","Got it","Түшүнүктүү"),
    Translation("Демо-пользователь","Demo user","Демо-колдонуучу"),
    Translation("Номер телефона не указан","Phone number not set","Телефон номери көрсөтүлгөн эмес"),
    Translation("Профиль создан","Profile created","Профиль түзүлгөн"),
    Translation("Изменить профиль","Edit profile","Профилди өзгөртүү"),
    Translation("БАЛАНС","BALANCE","БАЛАНС"),
    Translation("+ Пополнить","+ Top up","+ Толуктоо"),
    Translation("Валюта","Currency","Валюта"),
    Translation("Кыргызский сом (KGS)","Kyrgyz som (KGS)","Кыргыз сому (KGS)"),
    Translation("Кыргызский сом · KGS","Kyrgyz som · KGS","Кыргыз сому · KGS"),
    Translation("Расчёты в приложении отображаются в кыргызских сомах. 1 сом = 100 тыйынов.","Amounts are shown in Kyrgyz som. 1 som = 100 tyiyn.","Колдонмодогу эсептер кыргыз сому менен көрсөтүлөт. 1 сом = 100 тыйын."),
    Translation("Безопасность","Security","Коопсуздук"),
    Translation("Данные и доступ к геопозиции","Data and location access","Маалыматтар жана жайгашкан жерге уруксат"),
    Translation("Профиль и учебный баланс хранятся на этом устройстве. Номера банковских карт и пароли не запрашиваются. Геопозиция обновляется только при открытом приложении. Серверной авторизации пока нет.","Your profile and demo balance are stored on this device. Card numbers and passwords are not requested. Location updates run only while the app is open. Server sign-in is not connected.","Профиль жана демо-баланс ушул түзмөктө сакталат. Банк картасынын номери жана сырсөздөр суралбайт. Жайгашкан жер колдонмо ачык кезде гана жаңыланат. Сервердик кирүү азырынча жок."),
    Translation("Информация о машине","Vehicle information","Унаа тууралуу маалымат"),
    Translation("Добавить автомобиль","Add vehicle","Унаа кошуу"),
    Translation("Мои полисы","My insurance","Менин полистерим"),
    Translation("Страхование автомобиля","Vehicle insurance","Унааны камсыздандыруу"),
    Translation("Страховые полисы пока не подключены. Оформление и оплата ОСАГО в приложении недоступны.","Insurance is not connected. Motor insurance cannot be purchased in the app yet.","Камсыздандыруу полистери азырынча туташкан эмес. Колдонмодо ОСАГОну тариздөө жана төлөө жеткиликсиз."),
    Translation("О приложении","About","Колдонмо тууралуу"),
    Translation("Демонстрационное приложение зарядки электромобилей. Станции и операции вымышленные. Банки, платёжные системы и реальные зарядные станции не подключены.","EV charging demo app. Stations and transactions are fictional. Banks, payment systems and real chargers are not connected.","Электромобилдерди кубаттоо үчүн демо-колдонмо. Станциялар жана операциялар ойдон чыгарылган. Банктар, төлөм системалары жана чыныгы кубаттоо станциялары туташкан эмес."),
    Translation("Личный профиль сохраняется только на этом устройстве","Your profile is stored on this device only","Жеке профиль ушул түзмөктө гана сакталат"),
    Translation("Личные данные","Personal details","Жеке маалыматтар"),
    Translation("Мой автомобиль","My vehicle","Менин унаам"),
    Translation("Имя","Name","Аты"),
    Translation("Марка и модель","Make and model","Маркасы жана модели"),
    Translation("Телефон","Phone","Телефон"),
    Translation("Госномер","Licence plate","Мамлекеттик номери"),
    Translation("Сохранить","Save","Сактоо"),
    Translation("Отмена","Cancel","Жокко чыгаруу"),
    Translation("Энергия для всего Кыргызстана","Energy for all of Kyrgyzstan","Бүт Кыргызстан үчүн энергия"),
    Translation("Демо-данные. Станции вымышленные. Платежей нет.","Demo data. Fictional stations. No real payments.","Демо-маалыматтар. Станциялар ойдон чыгарылган. Чыныгы төлөмдөр жок."),
    Translation("Аппарат","Charger","Түзмөк"),
    Translation("аппарата","chargers","түзмөк"),
    Translation("аппаратов","chargers","түзмөк"),
    Translation("разъём","connector","туташтыргыч"),
    Translation("разъёмов","connectors","туташтыргыч"),
    Translation("Свободно","Available","Бош"),
    Translation("свободно","available","бош"),
    Translation("из","of","ичинен"),
    Translation("мест свободно","spots available","орун бош"),
    Translation("км по прямой","km straight-line distance","км түз аралык"),
    Translation("на место","per charger","ар бир түзмөккө"),
    Translation("демонстрационная станция","demo station","демо-станция"),
    Translation("Демо-зарядка","Demo charging","Демо-кубаттоо"),
    Translation("Учебное пополнение","Demo top-up","Демо-толуктоо"),
    Translation("Демо · без банковской операции","Demo · no bank transaction","Демо · банк операциясы жок"),
    Translation("Демо-уровень заряда","Demo battery charge","Батареянын демо-заряды"),
    Translation("Заряд батареи","Battery charge","Батареянын заряды"),
    Translation("процентов, демо","percent, demo","пайыз, демо"),
    Translation("ДЕМО","DEMO","ДЕМО"),
    Translation("Демо","Demo","Демо"),
    Translation("демо","demo","демо"),
    Translation("кВт·ч","kWh","кВт·саат"),
    Translation("кВтч","kWh","кВт·саат"),
    Translation("кВт","kW","кВт"),
    Translation("киловатт","kilowatts","киловатт"),
    Translation("сом","KGS","сом"),
    Translation("сек","sec","сек"),
    Translation("м","m","м"),
    Translation("Демо-график","Demo chart","Демо-график"),
    Translation("от","from","баштап"),
    Translation("до","to","чейин"),
    Translation("процентов","percent","пайыз"),
    Translation("мощность","power","кубаттуулук"),
    Translation("Масштаб","Zoom","Масштаб"),
    Translation("карта загружена","map loaded","карта жүктөлдү"),
    Translation("ожидание сети","waiting for network","тармак күтүлүүдө"),
)
private val translationByKey=translations.associateBy {it.ru}
private val translationsByLength=translations.sortedByDescending {it.ru.length}
