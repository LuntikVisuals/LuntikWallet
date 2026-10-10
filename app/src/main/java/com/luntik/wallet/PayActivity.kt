package com.luntik.wallet

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class PayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val amount = intent.getDoubleExtra("amount", 0.0)
        val order = intent.getStringExtra("order") ?: ""
        val title = intent.getStringExtra("title") ?: "Покупка"
        val store = WalletStore(this)
        val extra = CardExtra(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 120, 48, 48) }
        val head = TextView(this).apply { text = "$title\n${amount.toInt()} LC\nна карте ${"%.0f".format(store.cardBalance)} LC"; textSize = 20f }
        val pin = EditText(this).apply { hint = "пин с оборота карты, 4 цифры" }
        val cvc = EditText(this).apply { hint = "CVC, 3 цифры" }
        val note = TextView(this)
        val ok = Button(this).apply {
            text = "Оплатить"
            setOnClickListener {
                val typed = pin.text.toString()
                val typedCvc = cvc.text.toString()
                note.text = when {
                    extra.forever -> "Бан 2.3"
                    order.isBlank() -> "Нет номера"
                    extra.paid(order) -> "Уже оплачен"
                    typed != extra.pin || typed.length != 4 -> "Неверный пин"
                    typedCvc != store.cvc || typedCvc.length != 3 -> "Неверный CVC"
                    store.cardBalance < amount -> "На карте не хватает. В кликере ${"%.0f".format(store.balance)} LC"
                    else -> {
                        store.cardBalance -= amount
                        extra.markPaid(order)
                        extra.add("Оплата $order $amount LC")
                        setResult(RESULT_OK)
                        finish()
                        "ok"
                    }
                }
            }
        }
        root.addView(head); root.addView(pin); root.addView(cvc); root.addView(ok); root.addView(note)
        setContentView(root)
    }
}
