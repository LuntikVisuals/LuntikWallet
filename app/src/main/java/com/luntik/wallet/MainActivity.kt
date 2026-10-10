package com.luntik.wallet

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val notif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lp = window.attributes
        if (Build.VERSION.SDK_INT >= 30) display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { lp.preferredDisplayModeId = it.modeId } else lp.preferredRefreshRate = 120f
        window.attributes = lp
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (intent?.getBooleanExtra("ok", false) == true) WalletStore(this).linked = true
        setContent { WalletRoot() }
    }
}
private enum class Tab { CARD, CLICK, RULES, SET }
private val accents = listOf(0xFF8B9CFF, 0xFF5CFFB0, 0xFFFF8FB8, 0xFFFFE14A, 0xFF6FA84A)
private val bgs = listOf(0xFF07070C to 0xFF141428, 0xFF101820 to 0xFF1C2834, 0xFF1A1020 to 0xFF2A1830)

@Composable fun WalletRoot() {
    val ctx = LocalContext.current
    val store = remember { WalletStore(ctx) }
    val extra = remember { CardExtra(ctx) }
    val prefs = remember { ctx.getSharedPreferences("wallet_ui", 0) }
    var linked by remember { mutableStateOf(store.linked) }
    var code by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.CARD) }
    var bal by remember { mutableStateOf(store.balance) }
    var cardBal by remember { mutableStateOf(store.cardBalance) }
    var accent by remember { mutableLongStateOf(prefs.getLong("accent", accents[0])) }
    var bg by remember { mutableIntStateOf(prefs.getInt("bg", 0)) }
    var anim by remember { mutableStateOf(prefs.getBoolean("anim", true)) }
    var pinOnFlip by remember { mutableStateOf(prefs.getBoolean("pinFlip", true)) }
    var faceDown by remember { mutableStateOf(false) }
    var glass by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { faceDown = e.values[2] < -7f }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm.unregisterListener(listener) }
    }
    fun refresh() { bal = store.balance; cardBal = store.cardBalance }
    val pair = bgs[bg.coerceIn(0, bgs.lastIndex)]
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(pair.first), Color(pair.second)))).pointerInput(Unit) {
        detectHorizontalDragGestures { _, drag -> if (drag > 36) glass = true else if (drag < -36) glass = false }
    }) {
        if (!linked) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LuntikWallet", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (code == null) GlassBtn("Через LuntikStore", accent, anim) { val c = Random.nextInt(10, 99).toString(); code = c; try { ctx.startActivity(Intent("com.luntik.store.CONFIRM_WALLET").apply { setPackage("com.luntik.store"); putExtra("code", c); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {} }
                else { Text(code!!, color = Color(accent), fontSize = 56.sp); GlassBtn("Я ввёл верно", accent, anim) { store.linked = true; linked = true } }
            }
            return@Box
        }
        if (extra.forever) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Бан навсегда", color = Color(0xFFFF8A9A), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("2.3 ${extra.banReason}", color = Color.White, modifier = Modifier.padding(16.dp))
                Text("Перевыпуск закрыт", color = Color.White.copy(0.7f))
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(if (faceDown) "••••" else "Кликер ${"%.1f".format(bal)} · карта ${"%.1f".format(cardBal)}", color = Color.White, modifier = Modifier.padding(20.dp))
            Box(Modifier.weight(1f)) {
                AnimatedContent(tab, transitionSpec = { (slideInHorizontally(tween(if (anim) 280 else 1)) { it / 4 } + fadeIn(tween(if (anim) 220 else 1))) togetherWith fadeOut(tween(if (anim) 140 else 1)) }, label = "tab") { t ->
                    when (t) {
                        Tab.CARD -> CardTab(store, extra, accent, anim, pinOnFlip, faceDown, ::refresh)
                        Tab.CLICK -> ClickTab(store, extra, accent, anim, ::refresh)
                        Tab.RULES -> RulesTab(accent, extra.forever, extra.banReason)
                        Tab.SET -> SettingsTab(store, extra, accent, anim, pinOnFlip, { accent = it; prefs.edit().putLong("accent", it).apply() }, { bg = it; prefs.edit().putInt("bg", it).apply() }, { anim = it; prefs.edit().putBoolean("anim", it).apply() }, { pinOnFlip = it; prefs.edit().putBoolean("pinFlip", it).apply() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.08f)).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Tab.CARD to "Карта", Tab.CLICK to "Кликер", Tab.RULES to "Правила", Tab.SET to "Ещё").forEach { (t, n) ->
                    Text(n, color = if (tab == t) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == t) Color(accent) else Color.Transparent).clickable { tab = t }.padding(horizontal = 10.dp, vertical = 10.dp))
                }
            }
        }
        AnimatedVisibility(visible = glass, enter = slideInHorizontally(tween(if (anim) 280 else 1)) { -it } + fadeIn(tween(if (anim) 220 else 1)), exit = slideOutHorizontally(tween(if (anim) 220 else 1)) { -it } + fadeOut(tween(if (anim) 160 else 1))) {
            Column(Modifier.fillMaxHeight().width(250.dp).statusBarsPadding().clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(Color.White.copy(0.14f)).border(1.dp, Color.White.copy(0.28f), RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > 40) glass = false } }.padding(16.dp)) {
                Text("Стекло", color = Color.White, fontWeight = FontWeight.Bold)
                GlassBtn("Правила", accent, anim) { tab = Tab.RULES; glass = false }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { glass = false }.padding(8.dp))
            }
        }
    }
}
@Composable private fun CardTab(store: WalletStore, extra: CardExtra, accent: Long, anim: Boolean, pinOnFlip: Boolean, faceDown: Boolean, refresh: () -> Unit) {
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(store.phase) }
    var note by remember { mutableStateOf("") }
    var back by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var pinIn by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("card") }
    var why by remember { mutableStateOf(store.purpose) }
    var debit by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(store.holder) }
    var sur by remember { mutableStateOf(store.surname) }
    var tel by remember { mutableStateOf(store.phone) }
    var style by remember { mutableStateOf(CardDesign.LUNTIK.name) }
    var shine by remember { mutableStateOf(false) }
    var showCvc by remember { mutableStateOf(false) }
    var rePass by remember { mutableStateOf("") }
    var reCode by remember { mutableStateOf("") }
    var reStep by remember { mutableStateOf(0) }
    val turn = animateFloatAsState(if (back) 180f else 0f, if (anim) spring(dampingRatio = 0.72f, stiffness = 280f) else tween(1), label = "turn")
    val shineX = animateFloatAsState(if (shine && anim) 1.2f else -0.3f, tween(if (anim) 520 else 1), label = "shine")
    fun flip() { shine = true; if (back) back = false else if (!pinOnFlip || extra.pin.isBlank()) { if (extra.pin.isBlank()) askPin = true else back = true } else { askPin = true; pinIn = "" } }
    LaunchedEffect(phase) { while (phase == CardPhase.REVIEW || phase == CardPhase.TEST_WAIT || phase == CardPhase.MAKING) { delay(1500); store.tickCard(); phase = store.phase; refresh() } }
    LaunchedEffect(shine) { if (shine) { delay(540); shine = false } }
    LaunchedEffect(showCvc) { if (showCvc) { delay(3000); showCvc = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { GlassChip("Карта", sub == "card", accent) { sub = "card" }; Spacer(Modifier.width(6.dp)); GlassChip("Бонусы", sub == "bonus", accent) { sub = "bonus" }; Spacer(Modifier.width(6.dp)); GlassChip("История", sub == "hist", accent) { sub = "hist" } }
        if (sub == "bonus") { GlassBtn("Проверить бонус", accent, anim) { note = store.claimBonus(); if (note != "Бонус раз в 6 часов" && note != "Пусто") extra.add("Бонус: $note"); refresh() }; Text(note.ifBlank { store.bonusText }, color = Color.White); return@Column }
        if (sub == "hist") { extra.history().ifEmpty { listOf("Пусто") }.forEach { Text(it, color = Color.White.copy(0.8f), modifier = Modifier.padding(vertical = 4.dp)) }; return@Column }
        when (phase) {
            CardPhase.NONE -> GlassBtn("Выпустить карту", accent, anim) { store.issue(); phase = store.phase }
            CardPhase.REVIEW, CardPhase.TEST_WAIT, CardPhase.MAKING -> { Text(phase.name, color = Color.White); GlassBtn("Ускорить за 100 LC", accent, anim) { note = store.rushPaid(); phase = store.phase; refresh() } }
            CardPhase.REJECTED -> GlassBtn("Подать снова", accent, anim) { store.issue(); phase = store.phase }
            CardPhase.TEST -> {
                HintField("зачем карта: игры / магазин", why) { why = it }
                Row { GlassChip("Дебет", debit, accent) { debit = true }; Spacer(Modifier.width(8.dp)); GlassChip("Кредит", !debit, accent) { debit = false } }
                Text("!не писать реальные имя и номер!", color = Color(0xFFFFE08A), fontSize = 11.sp)
                HintField("имя, можно выдумать", name) { name = it }
                HintField("фамилия, можно пусто", sur) { sur = it }
                HintField("телефон, не свой номер", tel) { tel = it }
                CardDesign.entries.chunked(2).forEach { row -> Row { row.forEach { d -> GlassChip(d.title, style == d.name, accent) { style = d.name }; Spacer(Modifier.width(6.dp)) } } }
                GlassBtn("Отправить", accent, anim) { note = store.submitTest(why, debit, name, sur, tel, style); phase = store.phase }
            }
            CardPhase.READY -> {
                extra.ensureExp()
                Box(Modifier.fillMaxWidth().height(190.dp).graphicsLayer { rotationY = turn.value; cameraDistance = 14 * density }.clickable { flip() }) {
                    if (turn.value < 90f) CardArt(store, extra, false, faceDown, showCvc, shineX.value) { }
                    else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardArt(store, extra, true, faceDown, showCvc, 0f) { showCvc = true } }
                }
                GlassBtn("Скопировать номер", accent, anim) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pan", store.pan)); note = "номер скопирован" }
                GlassBtn(if (store.frozen) "Разморозить" else "Заморозить", accent, anim) { store.frozen = !store.frozen }
                if (reStep == 0) GlassBtn("Перевыпуск", accent, anim) { reStep = 1 }
                if (reStep == 1) { HintField("пароль перевыпуска", rePass) { rePass = it }; GlassBtn("Прислать код", accent, anim) { val r = store.startReissue(rePass); note = if (r.length == 4) "код в уведомлении" else r; if (r.length == 4) reStep = 2 } }
                if (reStep == 2) { HintField("код из уведомления", reCode) { reCode = it }; GlassBtn("Подтвердить", accent, anim) { if (store.confirmReissue(reCode)) { phase = store.phase; reStep = 0; note = "перевыпуск начат" } else note = "код не тот" } }
            }
        }
        if (askPin) { Text(if (extra.pin.isBlank()) "Придумай пин из 4 цифр" else "Введи пин", color = Color.White); HintField("4 цифры", pinIn) { pinIn = it.filter { ch -> ch.isDigit() }.take(4) }; GlassBtn("Ок", accent, anim) { if (pinIn.length == 4 && (extra.pin.isBlank() || extra.pin == pinIn)) { if (extra.pin.isBlank()) extra.pin = pinIn; back = true; askPin = false } else note = "Неверный пин" } }
        if (note.isNotBlank()) Text(note, color = Color(accent))
    }
}
@Composable private fun CardArt(store: WalletStore, extra: CardExtra, back: Boolean, hide: Boolean, showCvc: Boolean, shine: Float, onCvc: () -> Unit) {
    val d = runCatching { CardDesign.valueOf(store.design) }.getOrDefault(CardDesign.AURORA)
    val who = listOf(store.holder, store.surname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Luntik" }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(if (back) Color(0xFF1A1A24) else Brush.linearGradient(listOf(Color(d.a), Color(d.b)))).clickable(enabled = back, onClick = onCvc)) {
        Canvas(Modifier.fillMaxSize()) {
            if (!back) when (d) {
                CardDesign.LUNTIK -> drawCircle(Color(0xFFFF8FB8), 36.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.62f))
                CardDesign.SPONGE -> { repeat(6) { i -> drawCircle(Color(0xFFC98412), 9.dp.toPx(), Offset(size.width * (0.2f + (i % 3) * 0.2f), size.height * (0.35f + i / 3 * 0.28f))) }; drawCircle(Color(0xFFFF6AD5), 14.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.28f)) }
                CardDesign.SHREK -> { drawCircle(Color(0xFF3E7A32), 22.dp.toPx(), Offset(20.dp.toPx(), 22.dp.toPx())); drawCircle(Color(0xFF3E7A32), 22.dp.toPx(), Offset(size.width - 20.dp.toPx(), 22.dp.toPx())) }
                else -> {}
            }
            if (shine in 0f..1.1f) drawRect(Color.White.copy(0.22f), topLeft = Offset(size.width * (shine - 0.3f), 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height))
        }
        Column(Modifier.padding(16.dp)) {
            if (!back) { Text(who, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(if (hide) "•••• •••• •••• ••••" else store.pan, color = Color.White); Text(extra.exp, color = Color.White.copy(0.8f), fontSize = 12.sp) }
            else { Box(Modifier.fillMaxWidth().height(28.dp).background(Color.Black)); Spacer(Modifier.height(12.dp)); Text("CVC ${if (showCvc) store.cvc else "***"}   ${extra.exp}", color = Color.White, fontWeight = FontWeight.Bold); Text(who, color = Color.White.copy(0.85f)) }
        }
    }
}
@Composable private fun ClickTab(store: WalletStore, extra: CardExtra, accent: Long, anim: Boolean, refresh: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(false) }
    var shop by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("50") }
    var msg by remember { mutableStateOf("") }
    var check by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }
    var spots by remember { mutableStateOf(emptyList<Offset>()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); store.closeSession(now)
            if (store.pendingAmount > 0) { store.tickPayout(now); refresh() }
            if (store.sessionEnd > now && !store.antibotOff && store.autoUntil < now) {
                val due = ((store.sessionMin * 60_000L - (store.sessionEnd - now)) / 60_000L).toInt()
                if (due > store.checksDone && !check) { check = true; hits = 0; spots = List(5) { Offset(Random.nextFloat(), Random.nextFloat()) } }
            }
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectHorizontalDragGestures { _, drag -> if (drag < -30) panel = true } }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (store.clickBanUntil > now) "Бан" else if (store.sessionEnd > now) "Сессия" else "Готов", color = Color.White.copy(0.65f))
            Box(Modifier.size(132.dp).clip(CircleShape).background(Color(accent)).clickable(enabled = store.sessionEnd > now && store.clickBanUntil < now && !check) { msg = store.registerTap(System.currentTimeMillis()) ?: ""; refresh() }, contentAlignment = Alignment.Center) { Text("TAP", color = Color.Black, fontWeight = FontWeight.Bold) }
            if (store.sessionEnd <= now) GlassBtn(if (store.cooldownEnd > now || store.clickBanUntil > now) "Жди" else "Сессия", accent, anim) { store.startSession(now) }
            GlassBtn(if (shop) "Скрыть прокачку" else "Прокачка", accent, anim) { shop = !shop }
            if (shop) {
                GlassBtn("Тап +0.1 (80)", accent, anim) { if (store.balance >= 80) { store.balance -= 80; store.tapValue += 0.1 }; refresh() }
                GlassBtn("Кулдаун −1 (120)", accent, anim) { if (store.cooldownMin > 5 && store.balance >= 120) { store.balance -= 120; store.cooldownMin-- }; refresh() }
                GlassBtn("Сессия +1 (120)", accent, anim) { if (store.sessionMin < 15 && store.balance >= 120) { store.balance -= 120; store.sessionMin++ }; refresh() }
                GlassBtn("Снять антибот (5000)", accent, anim) { if (store.balance >= 5000) { store.balance -= 5000; store.antibotOff = true }; refresh() }
                GlassBtn("Автокликер на сессию (400)", accent, anim) { if (store.balance >= 400) { store.balance -= 400; store.autoUntil = System.currentTimeMillis() + store.sessionMin * 60_000L }; refresh() }
            }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFF8A9A), fontSize = 12.sp)
            if (check) spots.forEachIndexed { i, o -> if (i >= hits) Box(Modifier.offset(x = (o.x * 180).dp, y = (o.y * 70).dp).size(36.dp).clip(CircleShape).background(Color(0xFF5CFFB0)).clickable { hits++; if (hits >= 5) { store.checksDone++; check = false } }) }
        }
        AnimatedVisibility(visible = panel, modifier = Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally(tween(if (anim) 280 else 1)) { it } + fadeIn(tween(if (anim) 200 else 1)), exit = slideOutHorizontally(tween(if (anim) 200 else 1)) { it } + fadeOut(tween(if (anim) 140 else 1))) {
            Column(Modifier.fillMaxHeight().width(230.dp).background(Color.White.copy(0.14f)).padding(14.dp)) {
                Text("Вывод", color = Color.White, fontWeight = FontWeight.Bold)
                BasicTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) }, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White))
                GlassBtn("Вывести", accent, anim) { msg = store.requestPayout(amount.toDoubleOrNull() ?: 0.0); if (msg.startsWith("Заявка")) extra.add("Вывод $amount LC"); refresh() }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { panel = false })
            }
        }
    }
}
@Composable private fun SettingsTab(store: WalletStore, extra: CardExtra, accent: Long, anim: Boolean, pinOnFlip: Boolean, onAccent: (Long) -> Unit, onBg: (Int) -> Unit, onAnim: (Boolean) -> Unit, onPin: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var newPin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { accents.forEach { GlassChip(" ", accent == it, it) { onAccent(it) }; Spacer(Modifier.width(6.dp)) } }
        GlassBtn("Тёмный", accent, anim) { onBg(0) }; GlassBtn("Синий", accent, anim) { onBg(1) }; GlassBtn("Ночной", accent, anim) { onBg(2) }
        GlassBtn(if (anim) "Анимации вкл" else "Анимации выкл", accent, anim) { onAnim(!anim) }
        GlassBtn(if (pinOnFlip) "Пин при перевороте вкл" else "Пин при перевороте выкл", accent, anim) { onPin(!pinOnFlip) }
        HintField("новый пин, 4 цифры", newPin) { newPin = it.filter { ch -> ch.isDigit() }.take(4) }
        GlassBtn("Сменить пин", accent, anim) { if (newPin.length == 4) extra.pin = newPin }
        GlassBtn("Иконка: стекло", accent, anim) { setIcon(ctx, "IconDefault") }
        GlassBtn("Иконка: Лунтик", accent, anim) { setIcon(ctx, "IconLuntik") }
        GlassBtn("Иконка: Спанчбоб", accent, anim) { setIcon(ctx, "IconSponge") }
        GlassBtn("Иконка: Шрек", accent, anim) { setIcon(ctx, "IconShrek") }
    }
}
private fun setIcon(ctx: Context, which: String) {
    val pm = ctx.packageManager
    listOf("IconDefault", "IconLuntik", "IconSponge", "IconShrek").forEach { pm.setComponentEnabledSetting(ComponentName(ctx.packageName, ctx.packageName + "." + it), if (it == which) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
}
@Composable private fun HintField(hint: String, value: String, on: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(12.dp)) {
        if (value.isEmpty()) Text(hint, color = Color.White.copy(0.35f))
        BasicTextField(value, on, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White), singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
@Composable private fun GlassBtn(label: String, accent: Long, anim: Boolean, on: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && anim) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "btn")
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(14.dp)).background(Color(accent)).clickable(src, null, onClick = on).padding(12.dp)) { Text(label, color = Color.Black, fontWeight = FontWeight.Bold) }
}
@Composable private fun GlassChip(label: String, on: Boolean, accent: Long, click: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) Color(accent) else Color.White.copy(0.08f)).clickable(onClick = click).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(label, color = if (on) Color.Black else Color.White, fontSize = 12.sp) } }
