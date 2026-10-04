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
            CardPhase.MAKING -> if (now - phaseAt >= 30 * 60_000L) {
                phase = CardPhase.READY
                pan = buildString {
                    repeat(4) { append(Random.nextInt(1000, 9999)); if (it < 3) append(' ') }
                }
                writeStoreRecord()
            }
            else -> {}
        }
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

    private fun writeStoreRecord() {
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, "LuntikStore")
            dir.mkdirs()
            val o = JSONObject()
                .put("pan", pan)
                .put("design", design)
                .put("readyAt", System.currentTimeMillis())
            File(dir, "wallet_card.json").writeText(o.toString())
        } catch (_: Exception) {}
    }
}
