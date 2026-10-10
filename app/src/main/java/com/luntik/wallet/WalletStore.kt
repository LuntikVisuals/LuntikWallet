package com.luntik.wallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

enum class CardDesign(val title: String, val a: Long, val b: Long) {
    AURORA("Aurora Glass", 0xFF7EC8FF, 0xFFB8F0FF),
    OBSIDIAN("Obsidian Void", 0xFF1A1028, 0xFF9B5CFF),
    EMERALD("Emerald Tide", 0xFF0E6B5C, 0xFF5CFFD0),
    ROSE("Rose Nebula", 0xFFE8A0B8, 0xFFFFE0C8),
    POLAR("Polar Frost", 0xFFE8F4FF, 0xFFB8D8FF),
    LUNTIK("Лунтик", 0xFF6EA8FF, 0xFFB9D4FF),
    SPONGE("Спанчбоб", 0xFFFFE14A, 0xFFFFB703),
    SHREK("Шрек", 0xFF6FA84A, 0xFF2F6B32)
}
enum class CardPhase { NONE, REVIEW, REJECTED, TEST_WAIT, TEST, MAKING, READY }

class WalletStore(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("wallet", Context.MODE_PRIVATE)
    var linked: Boolean get() = p.getBoolean("linked", false); set(v) = p.edit().putBoolean("linked", v).apply()
    var balance: Double get() = p.getString("bal", "0")!!.toDouble(); set(v) = p.edit().putString("bal", v.toString()).apply()
    var cardBalance: Double get() = p.getString("cardBal", "0")!!.toDouble(); set(v) = p.edit().putString("cardBal", v.toString()).apply()
    var pendingAmount: Double get() = p.getString("pend", "0")!!.toDouble(); set(v) = p.edit().putString("pend", v.toString()).apply()
    var pendingAt: Long get() = p.getLong("pendAt", 0); set(v) = p.edit().putLong("pendAt", v).apply()
    var phase: CardPhase get() = runCatching { CardPhase.valueOf(p.getString("phase", "NONE")!!) }.getOrDefault(CardPhase.NONE); set(v) = p.edit().putString("phase", v.name).apply()
    var phaseAt: Long get() = p.getLong("phaseAt", 0); set(v) = p.edit().putLong("phaseAt", v).apply()
    var design: String get() = p.getString("design", CardDesign.AURORA.name)!!; set(v) = p.edit().putString("design", v).apply()
    var frozen: Boolean get() = p.getBoolean("frozen", false); set(v) = p.edit().putBoolean("frozen", v).apply()
    var pan: String get() = p.getString("pan", "")!!; set(v) = p.edit().putString("pan", v).apply()
    var cvc: String get() = p.getString("cvc", "")!!; set(v) = p.edit().putString("cvc", v).apply()
    var kind: String get() = p.getString("kind", "дебет")!!; set(v) = p.edit().putString("kind", v).apply()
    var holder: String get() = p.getString("holder", "")!!; set(v) = p.edit().putString("holder", v).apply()
    var surname: String get() = p.getString("surname", "")!!; set(v) = p.edit().putString("surname", v).apply()
    var phone: String get() = p.getString("phone", "")!!; set(v) = p.edit().putString("phone", v).apply()
    var purpose: String get() = p.getString("purpose", "")!!; set(v) = p.edit().putString("purpose", v).apply()
    var tapValue: Double get() = p.getString("tap", "0.1")!!.toDouble(); set(v) = p.edit().putString("tap", v.toString()).apply()
    var cooldownMin: Int get() = p.getInt("cd", 20); set(v) = p.edit().putInt("cd", v).apply()
    var sessionMin: Int get() = p.getInt("sess", 5); set(v) = p.edit().putInt("sess", v).apply()
    var antibotOff: Boolean get() = p.getBoolean("nobot", false); set(v) = p.edit().putBoolean("nobot", v).apply()
    var firstWithdraw: Boolean get() = p.getBoolean("firstW", true); set(v) = p.edit().putBoolean("firstW", v).apply()
    var freeWithdraw: Boolean get() = p.getBoolean("freeW", false); set(v) = p.edit().putBoolean("freeW", v).apply()
    var sessionEnd: Long get() = p.getLong("sEnd", 0); set(v) = p.edit().putLong("sEnd", v).apply()
    var cooldownEnd: Long get() = p.getLong("cEnd", 0); set(v) = p.edit().putLong("cEnd", v).apply()
    var clickBanUntil: Long get() = p.getLong("ban", 0); set(v) = p.edit().putLong("ban", v).apply()
    var lastTapAt: Long get() = p.getLong("lastTap", 0); set(v) = p.edit().putLong("lastTap", v).apply()
    var autoUntil: Long get() = p.getLong("auto", 0); set(v) = p.edit().putLong("auto", v).apply()
    var fails: Int get() = p.getInt("fails", 0); set(v) = p.edit().putInt("fails", v).apply()
    var bonusText: String get() = p.getString("bonus", "")!!; set(v) = p.edit().putString("bonus", v).apply()
    var lastBonusAt: Long get() = p.getLong("bonusAt", 0); set(v) = p.edit().putLong("bonusAt", v).apply()
    var checksDone: Int get() = p.getInt("checks", 0); set(v) = p.edit().putInt("checks", v).apply()
    var reissuePass: String get() = p.getString("rePass", "")!!; set(v) = p.edit().putString("rePass", v).apply()
    var reissueCode: String get() = p.getString("reCode", "")!!; set(v) = p.edit().putString("reCode", v).apply()
    private var told: String get() = p.getString("told", "")!!; set(v) = p.edit().putString("told", v).apply()

    fun tickCard() {
        val now = System.currentTimeMillis()
        when (phase) {
            CardPhase.REVIEW -> if (now - phaseAt >= 60 * 60_000L) {
                if (Random.nextInt(100) < 75) { phase = CardPhase.TEST_WAIT; phaseAt = now; notify("Заявка принята", "Тест придёт в течение часа") }
                else { phase = CardPhase.REJECTED; phaseAt = now; notify("Заявка отклонена", "Можно подать снова") }
            }
            CardPhase.TEST_WAIT -> if (now - phaseAt >= 60 * 60_000L) { phase = CardPhase.TEST; phaseAt = now; notify("Тест пришёл", "Заполни анкету") }
            CardPhase.MAKING -> if (now - phaseAt >= 30 * 60_000L) finishCard()
            else -> {}
        }
    }
    fun submitTest(why: String, debit: Boolean, name: String, sur: String, tel: String, style: String): String {
        if (name.trim().length < 2) return "Имя обязательно"
        if (why.trim().length < 3) return "Напиши, зачем карта"
        purpose = why.trim(); kind = if (debit) "дебет" else "кредит"
        holder = name.trim(); surname = sur.trim(); phone = tel.trim(); design = style
        phase = CardPhase.MAKING; phaseAt = System.currentTimeMillis()
        return "Анкета принята. Карта делается ~30 мин"
    }
    fun issue() { phase = CardPhase.REVIEW; phaseAt = System.currentTimeMillis(); told = "" }
    fun makePan() = buildString { repeat(4) { append(Random.nextInt(1000, 9999)); if (it < 3) append(' ') } }
    fun makeCvc() = Random.nextInt(100, 999).toString()
    fun reissue() { frozen = false; pan = ""; cvc = ""; issue() }
    fun startReissue(pass: String): String {
        if (reissuePass.isBlank()) reissuePass = pass
        if (pass != reissuePass) return "Неверный пароль"
        reissueCode = Random.nextInt(1000, 9999).toString()
        notify("Код перевыпуска", reissueCode)
        return reissueCode
    }
    fun confirmReissue(code: String): Boolean {
        if (code != reissueCode || reissueCode.isBlank()) return false
        reissueCode = ""; reissue(); return true
    }
    fun rushPaid(): String {
        if (phase != CardPhase.REVIEW && phase != CardPhase.TEST_WAIT && phase != CardPhase.MAKING) return "Нечего ускорять"
        if (cardBalance < 100.0) return "На карте нужно 100 LC"
        cardBalance -= 100.0
        when (phase) {
            CardPhase.REVIEW -> { phase = CardPhase.TEST_WAIT; phaseAt = System.currentTimeMillis() }
            CardPhase.TEST_WAIT -> { phase = CardPhase.TEST; phaseAt = System.currentTimeMillis() }
            CardPhase.MAKING -> finishCard()
            else -> {}
        }
        return "Ускорено"
    }
    fun registerTap(now: Long): String? {
        if (clickBanUntil > now) return "Бан кликера"
        val prev = lastTapAt; val gap = now - prev; lastTapAt = now
        if (prev != 0L && gap in 0..49) { clickBanUntil = now + 10 * 60_000L; sessionEnd = 0; notify("Античит", "Слишком быстрые тапы. Бан на 10 минут"); return "Бан 10 мин" }
        balance += tapValue; return null
    }
    private fun finishCard() {
        phase = CardPhase.READY
        pan = makePan(); cvc = makeCvc()
        writeStoreRecord(); notify("Карта готова", "$kind · $pan")
    }
    fun closeSession(now: Long) { if (sessionEnd in 1..now && cooldownEnd < now) { cooldownEnd = now + cooldownMin * 60_000L; sessionEnd = 0; autoUntil = 0 } }
    fun startSession(now: Long): String? {
        if (clickBanUntil > now) return "Бан кликера"
        closeSession(now)
        if (cooldownEnd > now) return "Кулдаун"
        if (sessionEnd > now) return null
        sessionEnd = now + sessionMin * 60_000L; fails = 0; checksDone = 0; lastTapAt = 0; return null
    }
    fun tickPayout(now: Long) {
        if (pendingAmount > 0 && pendingAt in 1..now) { cardBalance += pendingAmount; pendingAmount = 0.0; pendingAt = 0; writeStoreRecord(); notify("Вывод зачислен", "На карте ${"%.1f".format(cardBalance)} LC") }
    }
    fun requestPayout(raw: Double): String {
        if (phase != CardPhase.READY) return "Сначала выпусти карту"
        if (frozen) return "Карта заморожена"
        if (pendingAmount > 0) return "Уже есть вывод в пути"
        if (raw < 50 || raw > 50_000) return "От 50 до 50 000 LC"
        if (raw > balance) return "Не хватает на кликере"
        val fee = when { firstWithdraw || freeWithdraw -> 0; isHoliday() -> 5; else -> 10 }
        val got = raw * (100 - fee) / 100.0
        balance -= raw; firstWithdraw = false; freeWithdraw = false
        val wait = Random.nextLong(60_000L, 24 * 60 * 60_000L)
        pendingAmount = got; pendingAt = System.currentTimeMillis() + wait
        return "Заявка ${"%.1f".format(got)} LC. На карту примерно через ${(wait / 60_000L).coerceAtLeast(1)} мин."
    }
    fun claimBonus(): String {
        val now = System.currentTimeMillis()
        if (now - lastBonusAt < 6 * 60 * 60_000L) return "Бонус раз в 6 часов"
        lastBonusAt = now
        return when (Random.nextInt(100)) { in 0..7 -> { balance += 8.0; "+8 LC" }; in 8..11 -> { freeWithdraw = true; "Следующий вывод без комиссии" }; else -> "Пусто" }.also { bonusText = it }
    }
    private fun notify(title: String, text: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("wallet", "LuntikWallet", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(title.hashCode(), android.app.Notification.Builder(ctx, "wallet").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setAutoCancel(true).build())
        } catch (_: Exception) {}
    }
    private fun writeStoreRecord() {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "LuntikStore")
            dir.mkdirs()
            File(dir, "wallet_card.json").writeText(JSONObject().put("pan", pan).put("cvc", cvc).put("design", design).put("kind", kind).put("holder", holder).put("cardBalance", cardBalance).toString())
        } catch (_: Exception) {}
    }
}
fun isHoliday(): Boolean {
    val c = java.util.Calendar.getInstance()
    return (c.get(java.util.Calendar.MONTH) == 9 && c.get(java.util.Calendar.DAY_OF_MONTH) == 31) || (c.get(java.util.Calendar.MONTH) == 0 && c.get(java.util.Calendar.DAY_OF_MONTH) == 1) || (c.get(java.util.Calendar.MONTH) == 11 && c.get(java.util.Calendar.DAY_OF_MONTH) >= 24)
}
