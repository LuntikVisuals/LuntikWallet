    fun issue() { phase = CardPhase.REVIEW; phaseAt = System.currentTimeMillis(); told = "" }
    fun makePan() = buildString { repeat(4) { append(Random.nextInt(1000, 9999)); if (it < 3) append(' ') } }
    fun makeCvc() = Random.nextInt(100, 999).toString()
    fun reissue() { frozen = false; pan = ""; cvc = ""; issue() }
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
