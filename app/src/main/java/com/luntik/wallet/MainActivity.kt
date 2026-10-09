package com.luntik.wallet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (intent?.getBooleanExtra("ok", false) == true) WalletStore(this).linked = true
        setContent { WalletRoot() }
    }
}
private enum class Tab { CARD, CLICK, SET }
@Composable
fun WalletRoot() {
    val ctx = LocalContext.current
    val store = remember { WalletStore(ctx) }
    var linked by remember { mutableStateOf(store.linked) }
    var code by remember { mutableStateOf<String?>(null) }
    var left by remember { mutableIntStateOf(5) }
    var tab by remember { mutableStateOf(Tab.CARD) }
    var bal by remember { mutableStateOf(store.balance) }
    var cardBal by remember { mutableStateOf(store.cardBalance) }
    fun refresh() { bal = store.balance; cardBal = store.cardBalance }
    LaunchedEffect(code) {
        if (code == null) return@LaunchedEffect
        left = 5
        while (left > 0 && !store.linked) { delay(1000); left-- }
        if (!store.linked) code = null
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07070C), Color(0xFF141428), Color(0xFF07070C))))) {
        if (!linked) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LuntikWallet", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (code == null) GlassBtn("Зарегистрироваться через LuntikStore") {
                    val c = Random.nextInt(10, 99).toString(); code = c
                    try { ctx.startActivity(Intent("com.luntik.store.CONFIRM_WALLET").apply { setPackage("com.luntik.store"); putExtra("code", c); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
                } else {
                    Text(code!!, color = Color(0xFF8B9CFF), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    GlassBtn("Я ввёл верно") { store.linked = true; linked = true }
                }
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Кликер ${"%.1f".format(bal)} · карта ${"%.1f".format(cardBal)}", color = Color.White, modifier = Modifier.padding(20.dp))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.CARD -> CardTab(store, ::refresh)
                    Tab.CLICK -> ClickTab(store, ::refresh)
                    Tab.SET -> Text("Уведомление приходит, когда заявку приняли или отклонили.", color = Color.White.copy(0.7f), modifier = Modifier.padding(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.08f)).padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Tab.CARD to "Карта", Tab.CLICK to "Кликер", Tab.SET to "Настройки").forEach { (t, n) ->
                    Text(n, color = if (tab == t) Color.Black else Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == t) Color(0xFF8B9CFF) else Color.Transparent).clickable { tab = t }.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}
@Composable
private fun CardTab(store: WalletStore, refresh: () -> Unit) {
    var phase by remember { mutableStateOf(store.phase) }
    var sub by remember { mutableStateOf("card") }
    var note by remember { mutableStateOf("") }
    var back by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf(store.purpose) }
    var debit by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(store.holder) }
    var sur by remember { mutableStateOf(store.surname) }
    var tel by remember { mutableStateOf(store.phone) }
    var style by remember { mutableStateOf(CardDesign.LUNTIK.name) }
    LaunchedEffect(Unit) { while (true) { store.tickCard(); store.tickPayout(System.currentTimeMillis()); phase = store.phase; refresh(); delay(1000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Карта", sub == "card") { sub = "card" }
            GlassChip("Бонусы", sub == "bonus") { sub = "bonus" }
        }
        Spacer(Modifier.height(12.dp))
        if (sub == "bonus") {
            GlassBtn("Проверить бонус") { note = store.claimBonus(); refresh() }
            Text(note.ifBlank { store.bonusText }, color = Color.White.copy(0.7f))
            return@Column
        }
        when (phase) {
            CardPhase.NONE -> GlassBtn("Выпустить карту") { store.issue(); phase = store.phase }
            CardPhase.REVIEW -> { Text("Заявка на рассмотрении. Ответ до часа.", color = Color.White); GlassBtn("Ускорить за 100 LC") { note = store.rushReview(); refresh() } }
            CardPhase.REJECTED -> { Text("Заявка отклонена.", color = Color(0xFFFF8A9A)); GlassBtn("Подать снова") { store.issue(); phase = store.phase } }
            CardPhase.TEST_WAIT -> { Text("Заявку приняли. Тест придёт в течение часа.", color = Color.White); GlassBtn("Тест сейчас за 100 LC") { note = store.rushTest(); phase = store.phase; refresh() } }
            CardPhase.TEST -> {
                Text("Зачем карта", color = Color.White.copy(0.7f), fontSize = 12.sp); Field(why) { why = it }
                Row { GlassChip("Дебет", debit) { debit = true }; Spacer(Modifier.width(8.dp)); GlassChip("Кредит", !debit) { debit = false } }
                Text("Имя", color = Color.White.copy(0.7f), fontSize = 12.sp); Field(name) { name = it }
                Text("Фамилия можно не писать. !не писать реальную!", color = Color(0xFFFFE08A), fontSize = 11.sp); Field(sur) { sur = it }
                Text("Телефон. !не писать свой реальный номер!", color = Color(0xFFFFE08A), fontSize = 11.sp); Field(tel) { tel = it }
                CardDesign.entries.chunked(2).forEach { row -> Row { row.forEach { d -> GlassChip(d.title, style == d.name) { style = d.name }; Spacer(Modifier.width(8.dp)) } } }
                GlassBtn("Отправить тест") { note = store.submitTest(why, debit, name, sur, tel, style); phase = store.phase }
            }
            CardPhase.MAKING -> Text("Анкета принята. Карта делается ~30 минут.", color = Color.White)
            CardPhase.READY -> {
                CardFace(store, back)
                GlassBtn(if (back) "Лицевая сторона" else "Перевернуть карту") { back = !back }
                GlassBtn(if (store.frozen) "Разморозить" else "Заморозить") { store.frozen = !store.frozen }
                GlassBtn("Перевыпуск") { store.reissue(); phase = store.phase }
            }
        }
        if (note.isNotBlank()) Text(note, color = Color(0xFF8B9CFF))
    }
}
@Composable
private fun CardFace(store: WalletStore, back: Boolean) {
    val d = runCatching { CardDesign.valueOf(store.design) }.getOrDefault(CardDesign.AURORA)
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(d.a), Color(d.b))))) {
        Canvas(Modifier.fillMaxSize()) {
            when (d) {
                CardDesign.LUNTIK -> drawCircle(Color(0xFFFF8FB8), 36.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.62f))
                CardDesign.SPONGE -> { repeat(6) { i -> drawCircle(Color(0xFFC98412), 10.dp.toPx(), Offset(size.width * (0.18f + (i % 3) * 0.22f), size.height * (0.35f + i / 3 * 0.28f))) }; drawCircle(Color(0xFFFF6AD5), 16.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.28f)) }
                CardDesign.SHREK -> { drawCircle(Color(0xFF3E7A32), 28.dp.toPx(), Offset(18.dp.toPx(), 24.dp.toPx())); drawCircle(Color(0xFF3E7A32), 28.dp.toPx(), Offset(size.width - 18.dp.toPx(), 24.dp.toPx())) }
                else -> {}
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (!back) {
                Text(d.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(store.kind, color = Color.White.copy(0.8f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(store.pan, color = Color.White, fontSize = 18.sp)
                Text(listOf(store.holder, store.surname).filter { it.isNotBlank() }.joinToString(" "), color = Color.White.copy(0.85f))
            } else {
                Box(Modifier.fillMaxWidth().height(36.dp).background(Color.Black))
                Spacer(Modifier.height(16.dp))
                Text("CVC ${store.cvc}", color = Color.Black, modifier = Modifier.background(Color.White).padding(8.dp))
            }
        }
    }
}
@Composable
private fun ClickTab(store: WalletStore, refresh: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shop by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }
    var spots by remember { mutableStateOf(emptyList<Offset>()) }
    var amount by remember { mutableStateOf("50") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis(); store.closeSession(now); store.tickPayout(now)
            if (store.sessionEnd > now && !store.antibotOff && store.autoUntil < now) {
                val due = ((store.sessionMin * 60_000L - (store.sessionEnd - now)) / 60_000L).toInt()
                if (due > store.checksDone && !check) { check = true; hits = 0; spots = List(5) { Offset(Random.nextFloat() * 0.72f + 0.08f, Random.nextFloat() * 0.55f + 0.2f) } }
            }
            if (store.autoUntil > now && store.sessionEnd > now && store.clickBanUntil < now) { store.balance += store.tapValue; refresh() }
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectHorizontalDragGestures { _, drag -> if (drag < -40) panel = true } }) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (store.clickBanUntil > now) "Бан" else if (store.sessionEnd > now) "Сессия" else "Готов", color = Color.White.copy(0.6f), modifier = Modifier.padding(top = 12.dp))
            Box(Modifier.size(140.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8B9CFF), Color(0xFF2A2A44)))).clickable(enabled = store.sessionEnd > now && store.clickBanUntil < now && !check) { msg = store.registerTap(System.currentTimeMillis()) ?: ""; refresh() }, contentAlignment = Alignment.Center) { Text("TAP", color = Color.White) }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFF8A9A), fontSize = 12.sp)
            if (store.sessionEnd <= now) GlassBtn(if (store.clickBanUntil > now || store.cooldownEnd > now) "Жди" else "Начать сессию") { store.startSession(now) }
            GlassBtn(if (shop) "Скрыть прокачку" else "Прокачка") { shop = !shop }
            if (shop) {
                GlassBtn("Тап +0.1 (80)") { if (store.balance >= 80) { store.balance -= 80; store.tapValue += 0.1 }; refresh() }
                GlassBtn("Кулдаун −1 (120)") { if (store.cooldownMin > 5 && store.balance >= 120) { store.balance -= 120; store.cooldownMin-- }; refresh() }
            }
            if (check) Check(spots, hits, { hits++; if (hits >= 5) { store.checksDone++; check = false } }, { store.fails++; if (store.fails >= 3) { store.sessionEnd = 1; store.closeSession(now); check = false } else hits = 0 })
        }
        if (panel) Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(240.dp).background(Color(0xEE12121A)).padding(16.dp)) {
            Text("Вывод 50–50000", color = Color.White)
            BasicTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) }, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            GlassBtn("Вывести") { msg = store.requestPayout(amount.toDoubleOrNull() ?: 0.0); refresh() }
            Text(msg, color = Color.White.copy(0.7f), fontSize = 12.sp)
            Text("Закрыть", color = Color(0xFF8B9CFF), modifier = Modifier.clickable { panel = false })
        }
    }
}
@Composable
private fun Check(spots: List<Offset>, hits: Int, onHit: () -> Unit, onFail: () -> Unit) {
    LaunchedEffect(hits, spots) { delay(8000); if (hits < 5) onFail() }
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        Text("5 точек ($hits/5)", color = Color.White)
        spots.forEachIndexed { i, o -> if (i >= hits) Box(Modifier.offset(x = (o.x * 220).dp, y = (o.y * 120).dp).size(42.dp).clip(CircleShape).background(Color(0xFF5CFFB0)).clickable { onHit() }) }
    }
}
@Composable
private fun Field(value: String, on: (String) -> Unit) {
    BasicTextField(value, on, textStyle = TextStyle(color = Color.White), cursorBrush = SolidColor(Color.White), singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(12.dp))
}
@Composable
private fun GlassBtn(label: String, on: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.1f)).clickable(onClick = on).padding(14.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}
@Composable
private fun GlassChip(label: String, sel: Boolean, on: () -> Unit) {
    Text(label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) Color(0xFF8B9CFF) else Color.White.copy(0.08f)).clickable(onClick = on).padding(horizontal = 12.dp, vertical = 8.dp))
}
