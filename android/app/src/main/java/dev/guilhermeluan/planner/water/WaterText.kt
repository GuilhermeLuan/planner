package dev.guilhermeluan.planner.water

import dev.guilhermeluan.planner.ui.components.PtBr
import java.text.NumberFormat
import kotlin.math.ceil

/** Textos da aba Água em linguagem simples ("Faltam 800 ml — cerca de quatro copos."). */
object WaterText {
    const val GLASS_ML = 200
    const val BOTTLE_ML = 500

    private val GlassWords = listOf("um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez")

    fun ml(value: Int): String = NumberFormat.getIntegerInstance(PtBr).format(value)

    fun liters(ml: Int): String = when {
        ml < 1000 -> "${ml(ml)} ml"
        ml == 1000 -> "1 litro"
        else -> "${decimalLiters(ml)} litros"
    }

    /** Rótulo curto das sugestões de meta ("2,5 L"). */
    fun shortLiters(ml: Int): String = "${decimalLiters(ml)} L"

    fun decimalLiters(ml: Int): String =
        NumberFormat.getNumberInstance(PtBr).apply { maximumFractionDigits = 2 }.format(ml / 1000.0)

    fun remaining(consumedMl: Int, goalMl: Int): String {
        val left = goalMl - consumedMl
        if (left <= 0) return "Você bateu a meta do dia."
        val glasses = ceil(left / GLASS_ML.toDouble()).toInt()
        val count = GlassWords.getOrNull(glasses - 1) ?: glasses.toString()
        return "Faltam ${ml(left)} ml — cerca de $count ${if (glasses == 1) "copo" else "copos"}."
    }
}
