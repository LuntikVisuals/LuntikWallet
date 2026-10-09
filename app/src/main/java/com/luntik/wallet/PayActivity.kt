package com.luntik.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val amount = intent.getDoubleExtra("amount", 0.0)
        val order = intent.getStringExtra("order") ?: ""
        val title = intent.getStringExtra("title") ?: "Покупка"
        setContent {
            val store = remember { WalletStore(this) }
            val extra = remember { CardExtra(this) }
            var pin by remember { mutableStateOf("") }
            var msg by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(title, color = Color.White, fontSize = 22.sp)
                Text("${amount.toInt()} LC", color = Color.White, fontSize = 32.sp)
                Text("Заказ $order", color = Color.White.copy(0.6f))
                BasicTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(4) }, textStyle = TextStyle(color = Color.White, fontSize = 28.sp), cursorBrush = SolidColor(Color.White))
                Text("Оплатить", color = Color.Black, modifier = Modifier.padding(top = 16.dp).clickablePay {
                    msg = pay(store, extra, amount, order, pin)
                    if (msg == "ok") { setResult(RESULT_OK); finish() }
                })
                if (msg.isNotBlank() && msg != "ok") Text(msg, color = Color(0xFFFF8A9A))
            }
        }
    }
    private fun pay(store: WalletStore, extra: CardExtra, amount: Double, order: String, pin: String): String {
        if (extra.forever) return "Бан 2.3"
        if (order.isBlank()) return "Нет номера заказа"
        if (extra.paid(order)) return "Этот заказ уже оплачен"
        if (pin != extra.pin || pin.length != 4) return "Неверный пин"
        if (store.cardBalance < amount) return "На карте не хватает"
        store.cardBalance -= amount
        extra.markPaid(order)
        extra.add("Оплата $order $amount LC")
        return "ok"
    }
}
private fun Modifier.clickablePay(on: () -> Unit) = androidx.compose.foundation.clickable(onClick = on)
