package com.luntik.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RuleBlock(val title: String, val lines: List<String>)

private val blocks = listOf(
    RuleBlock("Кликер", listOf(
        "1.1 Тап только в открытую сессию.",
        "1.2 Быстрее 50 мс — бан кликера на 10 минут.",
        "1.3 Три провала античита подряд закрывают сессию.",
        "1.4 Чужой автокликер нельзя. Свой только из прокачки на одну сессию."
    )),
    RuleBlock("Баны", listOf(
        "2.1 Бан кликера не трогает карту и вывод, который уже в пути.",
        "2.2 Повтор быстрых тапов после бана продлевает его ещё на 10 минут.",
        "2.3 Отмыв денег — бан навсегда. Перевыпуск закрыт, снятия нет.",
        "2.4 Снять бан за лунткойны нельзя."
    )),
    RuleBlock("Аккаунт", listOf(
        "3.1 Вход только через LuntikStore.",
        "3.2 Чужой код подтверждения вводить нельзя.",
        "3.3 Кнопка «я ввёл верно» временная, пока Стор не отвечает."
    )),
    RuleBlock("Карта", listOf(
        "4.1 Пин из 4 цифр. Без него оборот не открывается.",
        "4.2 Заморозка останавливает вывод.",
        "4.3 Перевыпуск — новая заявка. При бане 2.3 его нет.",
        "4.4 В анкете нельзя писать реальные имя и номер."
    )),
    RuleBlock("Покупки", listOf(
        "5.1 Лунткойн нельзя купить за реальные деньги.",
        "5.2 Вывод на карту от 50 до 50 000 LC.",
        "5.3 Списание в Сторе только после пина и только на номер заказа.",
        "5.4 Один заказ дважды не оплачивается."
    ))
)

@Composable
fun RulesTab(accent: Long, banned: Boolean, reason: String) {
    var open by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<RuleBlock?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!open) {
            Box(Modifier.size(168.dp).clip(CircleShape).background(Color(accent)).clickable { open = true }, contentAlignment = Alignment.Center) {
                Text("Правила", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Text("Нажми, чтобы открыть разделы", color = Color.White.copy(0.6f), modifier = Modifier.padding(top = 12.dp))
        } else if (section == null) {
            Text("Разделы", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            blocks.forEach { b ->
                Text(b.title, color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(CircleShape).background(Color(accent)).clickable { section = b }.padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text("Назад", color = Color(accent), modifier = Modifier.clickable { open = false }.padding(8.dp))
        } else {
            Text(section!!.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            section!!.lines.forEach { Text(it, color = Color.White.copy(0.9f), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
            if (banned) Text("Сейчас бан 2.3: $reason. Перевыпуск закрыт.", color = Color(0xFFFF8A9A))
            Text("К разделам", color = Color(accent), modifier = Modifier.clickable { section = null }.padding(8.dp))
        }
    }
}
