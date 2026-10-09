package com.luntik.wallet

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        if (Build.VERSION.SDK_INT >= 30) display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { lp.preferredDisplayModeId = it.modeId }
        else lp.preferredRefreshRate = 120f
        window.attributes = lp
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (intent?.getBooleanExtra("ok", false) == true) WalletStore(this).linked = true
        setContent { WalletRoot() }
    }
}
private enum class Tab { CARD, CLICK, SET }
private val accents = listOf(0xFF8B9CFF, 0xFF5CFFB0, 0xFFFF8FB8, 0xFFFFE14A, 0xFF6FA84A)
private val bgs = listOf(0xFF07070C to 0xFF141428, 0xFF101820 to 0xFF1C2834, 0xFF1A1020 to 0xFF2A1830)

@Composable
fun WalletRoot() {
    val ctx = LocalContext.current
    val store = remember { WalletStore(ctx) }
    val prefs = remember { ctx.getSharedPreferences("wallet_ui", 0) }
    var linked by remember { mutableStateOf(store.linked) }
    var code by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.CARD) }
    var bal by remember { mutableStateOf(store.balance) }
    var cardBal by remember { mutableStateOf(store.cardBalance) }
    var accent by remember { mutableLongStateOf(prefs.getLong("accent", accents[0])) }
    var bg by remember { mutableIntStateOf(prefs.getInt("bg", 0)) }
    var glass by remember { mutableStateOf(false) }
    fun refresh() { bal = store.balance; cardBal = store.cardBalance }
    val pair = bgs[bg.coerceIn(0, bgs.lastIndex)]
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(pair.first), Color(pair.second)))).pointerInput(Unit) {
        detectHorizontalDragGestures { _, drag -> if (drag > 36) glass = true else if (drag < -36) glass = false }
    }) {
        if (!linked) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LuntikWallet", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (code == null) GlassBtn("Через LuntikStore", accent) {
                    val c = Random.nextInt(10, 99).toString(); code = c
                    try { ctx.startActivity(Intent("com.luntik.store.CONFIRM_WALLET").apply { setPackage("com.luntik.store"); putExtra("code", c); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
                } else { Text(code!!, color = Color(accent), fontSize = 56.sp); GlassBtn("Я ввёл верно", accent) { store.linked = true; linked = true } }
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Кликер ${"%.1f".format(bal)} · карта ${"%.1f".format(cardBal)}", color = Color.White, modifier = Modifier.padding(20.dp))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.CARD -> CardTab(store, accent, ::refresh)
                    Tab.CLICK -> ClickTab(store, accent, ::refresh)
                    Tab.SET -> SettingsTab(accent, bg, { accent = it; prefs.edit().putLong("accent", it).apply() }, { bg = it; prefs.edit().putInt("bg", it).apply() })
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.08f)).padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Tab.CARD to "Карта", Tab.CLICK to "Кликер", Tab.SET to "Настройки").forEach { (t, n) ->
                    Text(n, color = if (tab == t) Color.Black else Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == t) Color(accent) else Color.Transparent).clickable { tab = t }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
        AnimatedVisibility(visible = glass, enter = slideInHorizontally(tween(280)) { -it } + fadeIn(tween(220)), exit = slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(160))) {
            Column(Modifier.fillMaxHeight().width(250.dp).statusBarsPadding().clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(Color.White.copy(0.14f)).border(1.dp, Color.White.copy(0.28f), RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).padding(16.dp)) {
                Text("Стекло", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                GlassBtn("Карта", accent) { tab = Tab.CARD; glass = false }
                GlassBtn("Кликер", accent) { tab = Tab.CLICK; glass = false }
                GlassBtn("Настройки", accent) { tab = Tab.SET; glass = false }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { glass = false }.padding(8.dp))
            }
        }
    }
}
@Composable
private fun CardTab(store: WalletStore, accent: Long, refresh: () -> Unit) {
    var phase by remember { mutableStateOf(store.phase) }
    var note by remember { mutableStateOf("") }
    var back by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf("card") }
    var why by remember { mutableStateOf(store.purpose) }
    var debit by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(store.holder) }
    var sur by remember { mutableStateOf(store.surname) }
    var tel by remember { mutableStateOf(store.phone) }
    var style by remember { mutableStateOf(CardDesign.LUNTIK.name) }
    val flip = animateFloatAsState(if (back) 1f else 0f, tween(280), label = "flip")
    LaunchedEffect(phase) { while (phase == CardPhase.REVIEW || phase == CardPhase.TEST_WAIT || phase == CardPhase.MAKING) { delay(1500); store.tickCard(); phase = store.phase; refresh() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { GlassChip("Карта", sub == "card", accent) { sub = "card" }; Spacer(Modifier.width(8.dp)); GlassChip("Бонусы", sub == "bonus", accent) { sub = "bonus" } }
        if (sub == "bonus") { GlassBtn("Проверить бонус", accent) { note = store.claimBonus(); refresh() }; Text(note.ifBlank { store.bonusText }, color = Color.White); return@Column }
        when (phase) {
            CardPhase.NONE -> GlassBtn("Выпустить карту", accent) { store.issue(); phase = store.phase }
            CardPhase.REVIEW, CardPhase.TEST_WAIT, CardPhase.MAKING -> { Text(phase.name, color = Color.White); GlassBtn("Ускорить сейчас", accent) { phase = store.skipNow(); refresh() } }
            CardPhase.REJECTED -> GlassBtn("Подать снова", accent) { store.issue(); phase = store.phase }
            CardPhase.TEST -> {
                Field(why) { why = it }
                Row { GlassChip("Дебет", debit, accent) { debit = true }; Spacer(Modifier.width(8.dp)); GlassChip("Кредит", !debit, accent) { debit = false } }
                Text("!не писать реальные имя и номер!", color = Color(0xFFFFE08A), fontSize = 11.sp)
                Field(name) { name = it }; Field(sur) { sur = it }; Field(tel) { tel = it }
                CardDesign.entries.chunked(2).forEach { row -> Row { row.forEach { d -> GlassChip(d.title, style == d.name, accent) { style = d.name }; Spacer(Modifier.width(6.dp)) } } }
                GlassBtn("Отправить", accent) { note = store.submitTest(why, debit, name, sur, tel, style); phase = store.phase }
            }
            CardPhase.READY -> {
                CardFace(store, flip.value > 0.5f)
                GlassBtn(if (back) "Лицо" else "Перевернуть", accent) { back = !back }
                GlassBtn(if (store.frozen) "Разморозить" else "Заморозить", accent) { store.frozen = !store.frozen }
                GlassBtn("Перевыпуск", accent) { store.reissue(); phase = store.phase }
            }
        }
        if (note.isNotBlank()) Text(note, color = Color(accent))
    }
}
private fun WalletStore.skipNow(): CardPhase {
    when (phase) {
        CardPhase.REVIEW -> { phase = CardPhase.TEST_WAIT; phaseAt = System.currentTimeMillis() }
        CardPhase.TEST_WAIT -> { phase = CardPhase.TEST; phaseAt = System.currentTimeMillis() }
        CardPhase.MAKING -> { phase = CardPhase.READY; if (pan.isBlank()) pan = "4242 4242 4242 4242"; if (cvc.isBlank()) cvc = "123" }
        else -> {}
    }
    return phase
}
@Composable
private fun SettingsTab(accent: Long, bg: Int, onAccent: (Long) -> Unit, onBg: (Int) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Акцент", color = Color.White)
        Row { accents.forEach { GlassChip(" ", accent == it, it) { onAccent(it) }; Spacer(Modifier.width(6.dp)) } }
        Text("Фон, сразу", color = Color.White, modifier = Modifier.padding(top = 12.dp))
        GlassBtn("Тёмный", accent) { onBg(0) }
        GlassBtn("Синий", accent) { onBg(1) }
        GlassBtn("Ночной", accent) { onBg(2) }
        Text(if (bg == 0) "сейчас тёмный" else if (bg == 1) "сейчас синий" else "сейчас ночной", color = Color.White.copy(0.6f), fontSize = 12.sp)
        GlassBtn("Иконка: стекло", accent) { setIcon(ctx, "IconDefault") }
        GlassBtn("Иконка: Лунтик", accent) { setIcon(ctx, "IconLuntik") }
        GlassBtn("Иконка: Спанчбоб", accent) { setIcon(ctx, "IconSponge") }
        GlassBtn("Иконка: Шрек", accent) { setIcon(ctx, "IconShrek") }
    }
}
private fun setIcon(ctx: android.content.Context, which: String) {
    val pm = ctx.packageManager
    listOf("IconDefault", "IconLuntik", "IconSponge", "IconShrek").forEach { pm.setComponentEnabledSetting(ComponentName(ctx.packageName, ctx.packageName + "." + it), if (it == which) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
}
@Composable
private fun CardFace(store: WalletStore, back: Boolean) {
    val d = runCatching { CardDesign.valueOf(store.design) }.getOrDefault(CardDesign.AURORA)
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(d.a), Color(d.b))))) {
        Canvas(Modifier.fillMaxSize()) {
            when (d) {
                CardDesign.LUNTIK -> drawCircle(Color(0xFFFF8FB8), 36.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.62f))
                CardDesign.SPONGE -> drawCircle(Color(0xFFFF6AD5), 16.dp.toPx(), Offset(size.width * 0.8f, size.height * 0.3f))
                CardDesign.SHREK -> drawCircle(Color(0xFF3E7A32), 22.dp.toPx(), Offset(24.dp.toPx(), 24.dp.toPx()))
                else -> {}
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (!back) { Text(d.title, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(store.pan, color = Color.White) }
            else { Box(Modifier.fillMaxWidth().height(28.dp).background(Color.Black)); Spacer(Modifier.height(12.dp)); Text("CVC ${store.cvc}", color = Color.Black, modifier = Modifier.background(Color.White).padding(6.dp)) }
        }
    }
}
@Composable
private fun ClickTab(store: WalletStore, accent: Long, refresh: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(false) }
    var shop by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("50") }
    var msg by remember { mutableStateOf("") }
    var check by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }
    var spots by remember { mutableStateOf(emptyList<Offset>()) }
    val scale = animateFloatAsState(if (store.sessionEnd > now) 1.06f else 1f, tween(400), label = "tap")
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
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (store.clickBanUntil > now) "Бан" else if (store.sessionEnd > now) "Сессия" else "Готов", color = Color.White.copy(0.65f))
            Box(Modifier.scale(scale.value).size(132.dp).clip(CircleShape).background(Color(accent)).clickable(enabled = store.sessionEnd > now && store.clickBanUntil < now && !check) { msg = store.registerTap(System.currentTimeMillis()) ?: ""; refresh() }, contentAlignment = Alignment.Center) { Text("TAP", color = Color.Black, fontWeight = FontWeight.Bold) }
            if (store.sessionEnd <= now) GlassBtn(if (store.cooldownEnd > now || store.clickBanUntil > now) "Жди" else "Сессия", accent) { store.startSession(now) }
            GlassBtn(if (shop) "Скрыть прокачку" else "Прокачка", accent) { shop = !shop }
            if (shop) {
                GlassBtn("Тап +0.1 (80)", accent) { if (store.balance >= 80) { store.balance -= 80; store.tapValue += 0.1 }; refresh() }
                GlassBtn("Кулдаун −1 (120)", accent) { if (store.cooldownMin > 5 && store.balance >= 120) { store.balance -= 120; store.cooldownMin-- }; refresh() }
                GlassBtn("Сессия +1 (120)", accent) { if (store.sessionMin < 15 && store.balance >= 120) { store.balance -= 120; store.sessionMin++ }; refresh() }
            }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFF8A9A), fontSize = 12.sp)
            if (check) {
                Text("5 точек $hits/5", color = Color.White)
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    spots.forEachIndexed { i, o -> if (i >= hits) Box(Modifier.offset(x = (o.x * 220).dp, y = (o.y * 100).dp).size(40.dp).clip(CircleShape).background(Color(0xFF5CFFB0)).clickable { hits++; if (hits >= 5) { store.checksDone++; check = false } }) }
                }
            }
        }
        AnimatedVisibility(visible = panel, enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)), exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140))) {
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(230.dp).background(Color.White.copy(0.14f)).border(1.dp, Color.White.copy(0.25f)).padding(14.dp)) {
                Text("Вывод", color = Color.White, fontWeight = FontWeight.Bold)
                BasicTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) }, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White))
                GlassBtn("Вывести", accent) { msg = store.requestPayout(amount.toDoubleOrNull() ?: 0.0); refresh() }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { panel = false })
            }
        }
    }
}
@Composable private fun Field(value: String, on: (String) -> Unit) { BasicTextField(value, on, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White), singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(12.dp)) }
@Composable private fun GlassBtn(label: String, accent: Long, on: () -> Unit) { Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.10f)).border(1.dp, Color(accent).copy(0.45f), RoundedCornerShape(16.dp)).clickable(onClick = on).padding(12.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) } }
@Composable private fun GlassChip(label: String, sel: Boolean, accent: Long, on: () -> Unit) { Text(label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) Color(accent) else Color.White.copy(0.08f)).clickable(onClick = on).padding(horizontal = 10.dp, vertical = 8.dp)) }
