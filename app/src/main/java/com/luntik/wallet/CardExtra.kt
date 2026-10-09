package com.luntik.wallet

import android.content.Context
import kotlin.random.Random

class CardExtra(ctx: Context) {
    private val p = ctx.getSharedPreferences("wallet", Context.MODE_PRIVATE)
    var pin: String
        get() = p.getString("pin", "")!!
        set(v) = p.edit().putString("pin", v).apply()
    var exp: String
        get() = p.getString("exp", "")!!
        set(v) = p.edit().putString("exp", v).apply()
    fun ensureExp() {
        if (exp.isBlank()) exp = "%02d/%02d".format(Random.nextInt(1, 13), Random.nextInt(28, 32))
    }
    fun history(): List<String> = p.getString("hist", "")!!.split("\n").filter { it.isNotBlank() }.take(10)
    fun add(line: String) {
        val next = (listOf(line) + history()).take(10).joinToString("\n")
        p.edit().putString("hist", next).apply()
    }
}
