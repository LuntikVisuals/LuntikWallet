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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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

@Composable
fun WalletRoot() {
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
        if (extra.forever) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Бан навсегда", color = Color(0xFFFF8A9A), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("2.3 ${extra.banReason}", color = Color.White, modifier = Modifier.padding(16.dp))
                Text("Перевыпуск закрыт", color = Color.White.copy(0.7f))
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Кликер ${"%.1f".format(bal)} · карта ${"%.1f".format(cardBal)}", color = Color.White, modifier = Modifier.padding(20.dp))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.CARD -> CardTab(store, extra, accent, anim, ::refresh)
                    Tab.CLICK -> ClickTab(store, extra, accent, ::refresh)
                    Tab.RULES -> RulesTab(accent, extra.forever, extra.banReason)
                    Tab.SET -> SettingsTab(accent, anim, { accent = it; prefs.edit().putLong("accent", it).apply() }, { bg = it; prefs.edit().putInt("bg", it).apply() }, { anim = it; prefs.edit().putBoolean("anim", it).apply() })
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
                GlassBtn("Правила", accent) { tab = Tab.RULES; glass = false }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { glass = false }.padding(8.dp))
            }
        }
    }
}
@Composable
private fun CardTab(store: WalletStore, extra: CardExtra, accent: Long, anim: Boolean, refresh: () -> Unit) {
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
    val turn = animateFloatAsState(if (back) 1f else 0f, tween(if (anim) 300 else 1), label = "turn")
    val shineX = animateFloatAsState(if (shine && anim) 1f else 0f, tween(300), label = "shine")
    fun flip() { if (back) back = false else { askPin = true; pinIn = "" }; shine = anim }
    LaunchedEffect(phase) { while (phase == CardPhase.REVIEW || phase == CardPhase.TEST_WAIT || phase == CardPhase.MAKING) { delay(1500); store.tickCard(); phase = store.phase; refresh() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { GlassChip("Карта", sub == "card", accent) { sub = "card" }; Spacer(Modifier.width(6.dp)); GlassChip("Бонусы", sub == "bonus", accent) { sub = "bonus" }; Spacer(Modifier.width(6.dp)); GlassChip("История", sub == "hist", accent) { sub = "hist" } }
        if (sub == "bonus") { GlassBtn("Проверить бонус", accent) { note = store.claimBonus(); if (note != "Бонус раз в 6 часов" && note != "Пусто") extra.add("Бонус: $note"); refresh() }; Text(note.ifBlank { store.bonusText }, color = Color.White); return@Column }
        if (sub == "hist") { extra.history().ifEmpty { listOf("Пусто") }.forEach { Text(it, color = Color.White.copy(0.8f), modifier = Modifier.padding(vertical = 4.dp)) }; return@Column }
        when (phase) {
            CardPhase.NONE -> GlassBtn("Выпустить карту", accent) { store.issue(); phase = store.phase }
            CardPhase.REVIEW, CardPhase.TEST_WAIT, CardPhase.MAKING -> { Text(phase.name, color = Color.White); GlassBtn("Ускорить сейчас", accent) { phase = store.skipNow(); extra.ensureExp(); extra.add("Ускорение заявки"); refresh() } }
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
                extra.ensureExp()
                CardFace(store, extra, turn.value > 0.5f, shineX.value) { flip() }
                GlassBtn(if (back) "Лицо" else "Перевернуть", accent) { flip() }
                GlassBtn(if (store.frozen) "Разморозить" else "Заморозить", accent) { store.frozen = !store.frozen }
                GlassBtn(if (extra.forever) "Перевыпуск закрыт" else "Перевыпуск", accent) { if (!extra.forever) { store.reissue(); phase = store.phase } else note = "2.3 бан навсегда" }
            }
        }
        if (askPin) {
            Text(if (extra.pin.isBlank()) "Придумай пин из 4 цифр" else "Введи пин", color = Color.White)
            Field(pinIn) { pinIn = it.filter { ch -> ch.isDigit() }.take(4) }
            GlassBtn("Ок", accent) { if (pinIn.length == 4 && (extra.pin.isBlank() || extra.pin == pinIn)) { if (extra.pin.isBlank()) extra.pin = pinIn; back = true; askPin = false } else note = "Неверный пин" }
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
private fun SettingsTab(accent: Long, anim: Boolean, onAccent: (Long) -> Unit, onBg: (Int) -> Unit, onAnim: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row { accents.forEach { GlassChip(" ", accent == it, it) { onAccent(it) }; Spacer(Modifier.width(6.dp)) } }
        GlassBtn("Тёмный", accent) { onBg(0) }; GlassBtn("Синий", accent) { onBg(1) }; GlassBtn("Ночной", accent) { onBg(2) }
        GlassBtn(if (anim) "Анимации вкл" else "Анимации выкл", accent) { onAnim(!anim) }
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
private fun CardFace(store: WalletStore, extra: CardExtra, back: Boolean, shine: Float, onTap: () -> Unit) {
    val d = runCatching { CardDesign.valueOf(store.design) }.getOrDefault(CardDesign.AURORA)
    val who = listOf(store.holder, store.surname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Luntik" }
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(d.a), Color(d.b)))).clickable(onClick = onTap)) {
        Canvas(Modifier.fillMaxSize()) {
            when (d) {
                CardDesign.LUNTIK -> drawCircle(Color(0xFFFF8FB8), 36.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.62f))
                CardDesign.SPONGE -> { repeat(6) { i -> drawCircle(Color(0xFFC98412), 9.dp.toPx(), Offset(size.width * (0.2f + (i % 3) * 0.2f), size.height * (0.35f + i / 3 * 0.28f))) }; drawCircle(Color(0xFFFF6AD5), 14.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.28f)) }
                CardDesign.SHREK -> { drawCircle(Color(0xFF3E7A32), 22.dp.toPx(), Offset(20.dp.toPx(), 22.dp.toPx())); drawCircle(Color(0xFF3E7A32), 22.dp.toPx(), Offset(size.width - 20.dp.toPx(), 22.dp.toPx())) }
                else -> {}
            }
            if (shine > 0f) drawRect(Color.White.copy(0.2f), topLeft = Offset(size.width * (shine - 0.3f), 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height))
        }
        Column(Modifier.padding(16.dp)) {
            if (!back) { Text(who, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(store.pan, color = Color.White); Text(extra.exp, color = Color.White.copy(0.8f), fontSize = 12.sp) }
            else { Box(Modifier.fillMaxWidth().height(28.dp).background(Color.Black)); Spacer(Modifier.height(12.dp)); Text("CVC ${store.cvc}   ${extra.exp}", color = Color.White, fontWeight = FontWeight.Bold); Text(who, color = Color.White.copy(0.85f)) }
        }
    }
}
@Composable
private fun ClickTab(store: WalletStore, extra: CardExtra, accent: Long, refresh: () -> Unit) {
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
            if (store.sessionEnd <= now) GlassBtn(if (store.cooldownEnd > now || store.clickBanUntil > now) "Жди" else "Сессия", accent) { store.startSession(now) }
            GlassBtn(if (shop) "Скрыть прокачку" else "Прокачка", accent) { shop = !shop }
            if (shop) {
                GlassBtn("Тап +0.1 (80)", accent) { if (store.balance >= 80) { store.balance -= 80; store.tapValue += 0.1 }; refresh() }
                GlassBtn("Кулдаун −1 (120)", accent) { if (store.cooldownMin > 5 && store.balance >= 120) { store.balance -= 120; store.cooldownMin-- }; refresh() }
                GlassBtn("Сессия +1 (120)", accent) { if (store.sessionMin < 15 && store.balance >= 120) { store.balance -= 120; store.sessionMin++ }; refresh() }
                GlassBtn("Снять антибот (5000)", accent) { if (store.balance >= 5000) { store.balance -= 5000; store.antibotOff = true }; refresh() }
                GlassBtn("Автокликер на сессию (400)", accent) { if (store.balance >= 400) { store.balance -= 400; store.autoUntil = System.currentTimeMillis() + store.sessionMin * 60_000L }; refresh() }
            }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFF8A9A), fontSize = 12.sp)
            if (check) spots.forEachIndexed { i, o -> if (i >= hits) Box(Modifier.offset(x = (o.x * 180).dp, y = (o.y * 70).dp).size(36.dp).clip(CircleShape).background(Color(0xFF5CFFB0)).clickable { hits++; if (hits >= 5) { store.checksDone++; check = false } }) }
        }
        AnimatedVisibility(visible = panel, modifier = Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(200)), exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140))) {
            Column(Modifier.fillMaxHeight().width(230.dp).background(Color.White.copy(0.14f)).padding(14.dp)) {
                Text("Вывод", color = Color.White, fontWeight = FontWeight.Bold)
                BasicTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) }, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White))
                GlassBtn("Вывести", accent) { msg = store.requestPayout(amount.toDoubleOrNull() ?: 0.0); if (msg.startsWith("Заявка")) extra.add("Вывод $amount LC"); refresh() }
                Text("Закрыть", color = Color(accent), modifier = Modifier.clickable { panel = false })
            }
        }
    }
}
@Composable private fun Field(value: String, on: (String) -> Unit) { BasicTextField(value, on, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White), singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(12.dp)) }
@Composable private fun GlassBtn(label: String, accent: Long, on: () -> Unit) { Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.10f)).border(1.dp, Color(accent).copy(0.45f), RoundedCornerShape(16.dp)).clickable(onClick = on).padding(12.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) } }
@Composable private fun GlassChip(label: String, sel: Boolean, accent: Long, on: () -> Unit) { Text(label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) Color(accent) else Color.White.copy(0.08f)).clickable(onClick = on).padding(horizontal = 10.dp, vertical = 8.dp)) }
