package com.luntik.wallet

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
    POLAR("Polar Frost", 0xFFE8F4FF, 0xFFB8D8FF)
}

enum class CardPhase { NONE, REVIEW, REJECTED, MAKING, READY }

class WalletStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("wallet", Context.MODE_PRIVATE)

    var linked: Boolean
        get() = p.getBoolean("linked", false)
        set(v) = p.edit().putBoolean("linked", v).apply()
    var balance: Double
        get() = p.getString("bal", "0")!!.toDouble()
        set(v) = p.edit().putString("bal", v.toString()).apply()
    var cardBalance: Double
        get() = p.getString("cardBal", "0")!!.toDouble()
        set(v) = p.edit().putString("cardBal", v.toString()).apply()
    var pendingAmount: Double
        get() = p.getString("pend", "0")!!.toDouble()
        set(v) = p.edit().putString("pend", v.toString()).apply()
    var pendingAt: Long
        get() = p.getLong("pendAt", 0)
        set(v) = p.edit().putLong("pendAt", v).apply()
    var phase: CardPhase
        get() = CardPhase.valueOf(p.getString("phase", "NONE")!!)
        set(v) = p.edit().putString("phase", v.name).apply()
    var phaseAt: Long
        get() = p.getLong("phaseAt", 0)
        set(v) = p.edit().putLong("phaseAt", v).apply()
    var design: String
        get() = p.getString("design", CardDesign.AURORA.name)!!
        set(v) = p.edit().putString("design", v).apply()
    var frozen: Boolean
        get() = p.getBoolean("frozen", false)
        set(v) = p.edit().putBoolean("frozen", v).apply()
    var pan: String
        get() = p.getString("pan", "")!!
        set(v) = p.edit().putString("pan", v).apply()
    var tapValue: Double
        get() = p.getString("tap", "0.1")!!.toDouble()
        set(v) = p.edit().putString("tap", v.toString()).apply()
    var cooldownMin: Int
        get() = p.getInt("cd", 20)
        set(v) = p.edit().putInt("cd", v).apply()
    var sessionMin: Int
        get() = p.getInt("sess", 5)
        set(v) = p.edit().putInt("sess", v).apply()
    var antibotOff: Boolean
        get() = p.getBoolean("nobot", false)
        set(v) = p.edit().putBoolean("nobot", v).apply()
    var firstWithdraw: Boolean
        get() = p.getBoolean("firstW", true)
        set(v) = p.edit().putBoolean("firstW", v).apply()
    var freeWithdraw: Boolean
        get() = p.getBoolean("freeW", false)
        set(v) = p.edit().putBoolean("freeW", v).apply()
    var sessionEnd: Long
        get() = p.getLong("sEnd", 0)
        set(v) = p.edit().putLong("sEnd", v).apply()
    var cooldownEnd: Long
        get() = p.getLong("cEnd", 0)
        set(v) = p.edit().putLong("cEnd", v).apply()
    var autoUntil: Long
        get() = p.getLong("auto", 0)
        set(v) = p.edit().putLong("auto", v).apply()
    var autoStockAt: Long
        get() = p.getLong("stock", 0)
        set(v) = p.edit().putLong("stock", v).apply()
    var fails: Int
        get() = p.getInt("fails", 0)
        set(v) = p.edit().putInt("fails", v).apply()
    var bonusText: String
        get() = p.getString("bonus", "")!!
        set(v) = p.edit().putString("bonus", v).apply()
    var lastBonusAt: Long
        get() = p.getLong("bonusAt", 0)
        set(v) = p.edit().putLong("bonusAt", v).apply()
    var checksDone: Int
        get() = p.getInt("checks", 0)
        set(v) = p.edit().putInt("checks", v).apply()

    fun tickCard() {
        val now = System.currentTimeMillis()
        when (phase) {
            CardPhase.REVIEW -> if (now - phaseAt >= 60 * 60_000L) {
                if (Random.nextInt(100) < 75) {
                    phase = CardPhase.MAKING
                    phaseAt = now
                } else {
                    phase = CardPhase.REJECTED
                    phaseAt = now
                }
            }
            CardPhase.MAKING -> if (now - phaseAt >= 30 * 60_000L) finishCard()
            else -> {}
        }
    }

    fun rushReview(): String {
        if (phase != CardPhase.REVIEW) return "Нет заявки"
        if (balance < 100) return "Нужно 100 LC"
        balance -= 100
        phaseAt = System.currentTimeMillis() - 59 * 60_000L
        return "Рассмотрение ускорено: ответ примерно через минуту"
    }

    fun issue() {
        phase = CardPhase.REVIEW
        phaseAt = System.currentTimeMillis()
        design = CardDesign.entries.random().name
    }

    fun reissue() {
        frozen = false
        pan = ""
        issue()
    }

    private fun finishCard() {
        phase = CardPhase.READY
        pan = buildString {
            repeat(4) { append(Random.nextInt(1000, 9999)); if (it < 3) append(' ') }
        }
        writeStoreRecord()
    }

    fun closeSession(now: Long) {
        if (sessionEnd in 1..now && cooldownEnd < now) {
            cooldownEnd = now + cooldownMin * 60_000L
            sessionEnd = 0
            autoUntil = 0
        }
    }

    fun startSession(now: Long): String? {
        closeSession(now)
        if (cooldownEnd > now) return "Кулдаун"
        if (sessionEnd > now) return null
        sessionEnd = now + sessionMin * 60_000L
        fails = 0
        checksDone = 0
        return null
    }

    fun tickPayout(now: Long) {
        if (pendingAmount > 0 && pendingAt in 1..now) {
            cardBalance += pendingAmount
            pendingAmount = 0
            pendingAt = 0
            writeStoreRecord()
        }
    }

    fun requestPayout(raw: Double): String {
        if (phase != CardPhase.READY) return "Сначала выпусти карту"
        if (frozen) return "Карта заморожена"
        if (pendingAmount > 0) return "Уже есть вывод в пути"
        if (raw < 50 || raw > 50_000) return "От 50 до 50 000 LC"
        if (raw > balance) return "Не хватает на кликере"
        val fee = when {
            firstWithdraw || freeWithdraw -> 0
            isHoliday() -> 5
            else -> 10
        }
        val got = raw * (100 - fee) / 100.0
        balance -= raw
        firstWithdraw = false
        freeWithdraw = false
        val wait = Random.nextLong(60_000L, 24 * 60 * 60_000L)
        pendingAmount = got
        pendingAt = System.currentTimeMillis() + wait
        val mins = (wait / 60_000L).coerceAtLeast(1)
        return "Заявка ${"%.1f".format(got)} LC. На карту примерно через $mins мин. Максимум 24 ч."
    }

    fun claimBonus(): String {
        val now = System.currentTimeMillis()
        if (now - lastBonusAt < 6 * 60 * 60_000L) {
            val left = 6 * 60 - (now - lastBonusAt) / 60_000L
            return "Бонус раз в 6 часов. Ещё ~$left мин"
        }
        lastBonusAt = now
        return when (Random.nextInt(100)) {
            in 0..7 -> { balance += 8; "+8 LC" }
            in 8..11 -> { freeWithdraw = true; "Следующий вывод без комиссии" }
            else -> "Пусто"
        }.also { bonusText = it }
    }

    private fun writeStoreRecord() {
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, "LuntikStore")
            dir.mkdirs()
            File(dir, "wallet_card.json").writeText(
                JSONObject()
                    .put("pan", pan)
                    .put("design", design)
                    .put("cardBalance", cardBalance)
                    .put("readyAt", System.currentTimeMillis())
                    .toString()
            )
        } catch (_: Exception) {}
    }
}

fun isHoliday(): Boolean {
    val c = java.util.Calendar.getInstance()
    val m = c.get(java.util.Calendar.MONTH)
    val d = c.get(java.util.Calendar.DAY_OF_MONTH)
    return (m == 9 && d == 31) || (m == 0 && d == 1) || (m == 11 && d >= 24)
}
