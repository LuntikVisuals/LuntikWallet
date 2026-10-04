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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                Text("жидкое стекло", color = Color.White.copy(0.5f))
                Spacer(Modifier.height(28.dp))
                if (code == null) {
                    GlassBtn("Зарегистрироваться через LuntikStore") {
                        val c = Random.nextInt(10, 99).toString()
                        code = c
                        val i = Intent("com.luntik.store.CONFIRM_WALLET").apply {
                            setPackage("com.luntik.store")
                            putExtra("code", c)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { ctx.startActivity(i) } catch (_: Exception) {}
                    }
                } else {
                    Text(code!!, color = Color(0xFF8B9CFF), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text("У тебя $left сек. Введи код в LuntikStore", color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    GlassBtn("Я ввёл верно") { store.linked = true; linked = true }
                }
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("${"%.1f".format(bal)} LC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.CARD -> CardTab(store)
                    Tab.CLICK -> ClickTab(store) { bal = store.balance }
                    Tab.SET -> Text("Уведомления запрашиваются при запуске. Карта пишется в Documents/LuntikStore.", color = Color.White.copy(0.7f), modifier = Modifier.padding(20.dp))
                }
            }
            Island(tab) { tab = it }
        }
    }
}

@Composable
private fun Island(tab: Tab, on: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.08f)).border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(28.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Tab.CARD to "Карта", Tab.CLICK to "Кликер", Tab.SET to "Настройки").forEach { (t, n) ->
            Text(n, color = if (tab == t) Color.Black else Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (tab == t) Color(0xFF8B9CFF) else Color.Transparent).clickable { on(t) }.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun CardTab(store: WalletStore) {
    var phase by remember { mutableStateOf(store.phase) }
    var tick by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf("card") }
    LaunchedEffect(Unit) { while (true) { store.tickCard(); phase = store.phase; tick++; delay(1000) } }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Карта", sub == "card") { sub = "card" }
            GlassChip("Бонусы", sub == "bonus") { sub = "bonus" }
        }
        Spacer(Modifier.height(16.dp))
        if (sub == "bonus") {
            Text(store.bonusText.ifBlank { "Пока пусто" }, color = Color.White.copy(0.7f))
            Spacer(Modifier.height(12.dp))
            GlassBtn("Проверить бонус") {
                when (Random.nextInt(3)) {
                    0 -> { store.balance += 25; store.bonusText = "+25 LC" }
                    1 -> { store.freeWithdraw = true; store.bonusText = "Следующий вывод без комиссии" }
                    else -> store.bonusText = "Пусто, зайди позже"
                }
                tick++
            }
            return@Column
        }
        when (phase) {
            CardPhase.NONE -> GlassBtn("Выпустить карту") { store.issue(); phase = store.phase }
            CardPhase.REVIEW -> Text("Заявка на рассмотрении. Ответ в течение часа.", color = Color.White)
            CardPhase.REJECTED -> {
                Text("Отклонено. Можно подать снова.", color = Color(0xFFFF8A9A))
                Spacer(Modifier.height(8.dp))
                GlassBtn("Подать снова") { store.issue(); phase = store.phase }
            }
            CardPhase.MAKING -> Text("Одобрено. Карта делается ~30 минут.", color = Color.White)
            CardPhase.READY -> {
                val d = CardDesign.valueOf(store.design)
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(d.a), Color(d.b)))).padding(16.dp)) {
                    Column {
                        Text("LUNTIK", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(d.title, color = Color.White.copy(0.8f), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(store.pan, color = Color.White, fontSize = 18.sp)
                        if (store.frozen) Text("ЗАМОРОЖЕНА", color = Color(0xFFFFE08A))
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassBtn(if (store.frozen) "Разморозить" else "Заморозить") { store.frozen = !store.frozen; tick++ }
                Spacer(Modifier.height(8.dp))
                GlassBtn("Перевыпуск") { store.reissue(); phase = store.phase }
            }
        }
    }
}

@Composable
private fun ClickTab(store: WalletStore, onBal: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var shop by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf(false) }
    var circles by remember { mutableIntStateOf(1) }
    var hits by remember { mutableIntStateOf(0) }
    var spots by remember { mutableStateOf(listOf(Offset(0.3f, 0.4f))) }
    var amount by remember { mutableStateOf("50") }
    var msg by remember { mutableStateOf("") }
    val pulse = rememberInfiniteTransition(label = "p")
    val s = pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (store.sessionEnd > now && !store.antibotOff && store.autoUntil < now) {
                val elapsed = store.sessionMin * 60_000L - (store.sessionEnd - now)
                if (elapsed > 0 && elapsed / 60_000L >= 1 && !check && elapsed % 60_000L < 1200) {
                    check = true; circles = 1; hits = 0
                    spots = listOf(Offset(Random.nextFloat() * 0.7f + 0.1f, Random.nextFloat() * 0.5f + 0.2f))
                }
            }
            if (store.autoUntil > now && store.sessionEnd > now) { store.balance += store.tapValue; onBal() }
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectHorizontalDragGestures { _, drag -> if (drag < -40) panel = true } }) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(0.1f)).clickable { shop = !shop }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(22.dp)) {
                        drawCircle(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                        drawLine(Color.White, Offset(size.width / 2, size.height * 0.28f), Offset(size.width / 2, size.height * 0.68f), 3f, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * 0.32f, size.height * 0.5f), Offset(size.width / 2, size.height * 0.7f), 3f, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * 0.68f, size.height * 0.5f), Offset(size.width / 2, size.height * 0.7f), 3f, StrokeCap.Round)
                    }
                }
            }
            val active = store.sessionEnd > now
            val cd = store.cooldownEnd > now
            Text(if (active) "Сессия" else if (cd) "Кулдаун" else "Готов", color = Color.White.copy(0.6f))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size((140 * s.value).dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF8B9CFF), Color(0xFF2A2A44)))).clickable(enabled = active && !check) { store.balance += store.tapValue; onBal() }, contentAlignment = Alignment.Center) {
                Text("TAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(16.dp))
            if (!active) GlassBtn(if (cd) "Жди кулдаун" else "Начать сессию") { if (!cd) { store.sessionEnd = now + store.sessionMin * 60_000L; store.fails = 0 } }
            if (shop) Shop(store)
            if (check) Check(circles, spots, hits, {
                hits++
                if (hits >= circles) {
                    if (circles == 1) {
                        circles = 2; hits = 0
                        spots = List(2) { Offset(Random.nextFloat() * 0.7f + 0.1f, Random.nextFloat() * 0.5f + 0.2f) }
                    } else check = false
                }
            }, {
                store.fails++
                if (store.fails >= 3) { store.sessionEnd = 0; store.cooldownEnd = now + store.cooldownMin * 60_000L; check = false }
                else { hits = 0; circles = 1 }
            })
        }
        if (panel) {
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(240.dp).background(Color(0xEE12121A)).padding(16.dp)) {
                Text("Вывод", color = Color.White, fontWeight = FontWeight.Bold)
                Text("От 50 LC", color = Color.White.copy(0.6f), fontSize = 12.sp)
                Text("Накоплено ${"%.1f".format(store.balance)}", color = Color(0xFF8B9CFF))
                val fee = when { store.firstWithdraw || store.freeWithdraw -> 0; isHoliday() -> 5; else -> 10 }
                Text("Комиссия $fee%", color = Color.White.copy(0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                GlassBtn("Вывести 50") {
                    val n = amount.toDoubleOrNull() ?: 50.0
                    if (n < 50 || n > store.balance) msg = "Минимум 50 и не больше баланса"
                    else {
                        val got = n * (100 - fee) / 100.0
                        store.balance -= n
                        store.firstWithdraw = false
                        store.freeWithdraw = false
                        msg = "Выведено ${"%.1f".format(got)} LC"
                        onBal()
                    }
                }
                Text(msg, color = Color.White.copy(0.7f), fontSize = 12.sp)
                Text("Закрыть", color = Color(0xFF8B9CFF), modifier = Modifier.clickable { panel = false })
            }
        }
    }
}

@Composable
private fun Shop(store: WalletStore) {
    Column(Modifier.padding(16.dp)) {
        Text("Прокачка", color = Color.White, fontWeight = FontWeight.Bold)
        GlassBtn("Тап +0.1 (80 LC)") { buy(store, 80.0) { store.tapValue += 0.1 } }
        GlassBtn("Кулдаун −1 мин (120 LC)") { if (store.cooldownMin > 5) buy(store, 120.0) { store.cooldownMin-- } }
        GlassBtn("Сессия +1 мин (120 LC)") { if (store.sessionMin < 15) buy(store, 120.0) { store.sessionMin++ } }
        GlassBtn("Снять антибот (5000 LC)") { buy(store, 5000.0) { store.antibotOff = true } }
        val stock = System.currentTimeMillis() - store.autoStockAt > 60 * 60_000L
        GlassBtn(if (stock) "Автокликер на сессию (400 LC)" else "Автокликера нет") {
            if (stock) buy(store, 400.0) {
                store.autoUntil = System.currentTimeMillis() + store.sessionMin * 60_000L
                store.autoStockAt = System.currentTimeMillis()
            }
        }
    }
}

private fun buy(store: WalletStore, price: Double, apply: () -> Unit) {
    if (store.balance >= price) { store.balance -= price; apply() }
}

@Composable
private fun Check(n: Int, spots: List<Offset>, hits: Int, onHit: () -> Unit, onFail: () -> Unit) {
    LaunchedEffect(n, hits) { delay(if (n == 1) 5000 else 7000); if (hits < n) onFail() }
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Text("Проверка: нажми круги ($hits/$n)", color = Color.White, modifier = Modifier.padding(8.dp))
        spots.forEachIndexed { i, o ->
            if (i >= hits) {
                Box(Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart).offset(x = (o.x * 260).dp, y = (o.y * 160).dp).size(48.dp).clip(CircleShape).background(Color(0xFF5CFFB0)).clickable { onHit() })
            }
        }
    }
}

@Composable
private fun GlassBtn(label: String, on: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.1f)).border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(16.dp)).clickable(onClick = on).padding(14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassChip(label: String, sel: Boolean, on: () -> Unit) {
    Text(label, color = if (sel) Color.Black else Color.White, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (sel) Color(0xFF8B9CFF) else Color.White.copy(0.08f)).clickable(onClick = on).padding(horizontal = 14.dp, vertical = 8.dp))
}

private fun isHoliday(): Boolean {
    val c = java.util.Calendar.getInstance()
    val m = c.get(java.util.Calendar.MONTH)
    val d = c.get(java.util.Calendar.DAY_OF_MONTH)
    return (m == 9 && d == 31) || (m == 0 && d == 1) || (m == 11 && d >= 24)
}
