package dev.guilhermeluan.planner.widget

import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.PlannedDose
import dev.guilhermeluan.planner.ui.components.PtBr
import dev.guilhermeluan.planner.water.WaterText
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant

/** Quanto o botão do widget fica confirmado depois do toque. */
val CONFIRMATION_DURATION: Duration = Duration.ofSeconds(3)

/** Até quando a confirmação de um toque em [now] vale; um novo toque reinicia a janela. */
fun confirmationUntil(now: Instant): Instant = now.plus(CONFIRMATION_DURATION)

enum class QuickKind { Water, Medicine }

/**
 * O que um botão do widget mostra depois do toque: [title] e [detail] são as duas linhas abaixo do ícone `check`,
 * [description] é o que o leitor de tela fala. Vale até [until], exclusive.
 */
data class QuickConfirmation(
    val kind: QuickKind,
    val title: String,
    val detail: String,
    val description: String,
    val until: Instant,
) {
    fun isActive(now: Instant): Boolean = now.isBefore(until)
}

/** A Dose pendente de horário mais cedo, ou nula se todas já foram registradas. */
fun nextPendingDose(doses: List<PlannedDose>): PlannedDose? =
    doses.filter { it.status == DoseStatus.PENDING }.minByOrNull { it.time }

object QuickText {
    private const val SHORT_NAME_LIMIT = 8

    fun waterTotal(totalMl: Int): String = "${WaterText.shortLiters(totalMl)} hoje"

    /** "1,45 litro", "2,5 litros", "200 ml": o plural começa em 2 litros. */
    fun litersSpoken(ml: Int): String {
        if (ml < 1000) return "$ml ml"
        val number = NumberFormat.getNumberInstance(PtBr).apply { maximumFractionDigits = 2 }.format(ml / 1000.0)
        return "$number ${if (ml < 2000) "litro" else "litros"}"
    }

    fun waterConfirmation(glassMl: Int, totalMl: Int, until: Instant) = QuickConfirmation(
        kind = QuickKind.Water,
        title = "+$glassMl ml",
        detail = waterTotal(totalMl),
        description = "Água registrada, ${litersSpoken(totalMl)} hoje",
        until = until,
    )

    fun doseConfirmation(dose: PlannedDose, until: Instant) = QuickConfirmation(
        kind = QuickKind.Medicine,
        title = "Tomado",
        detail = "${shortName(dose.name)} · ${dose.time}",
        description = "Remédio tomado, ${dose.name} às ${dose.time}",
        until = until,
    )

    /** Cabe no botão: "Vitamina D" vira "Vit. D", "Paracetamol" vira "Paracet.". */
    fun shortName(name: String): String {
        if (name.length <= SHORT_NAME_LIMIT) return name
        val words = name.split(' ')
        val first = words.first()
        return if (words.size == 1) {
            first.take(SHORT_NAME_LIMIT - 1) + "."
        } else {
            (listOf(first.take(3) + ".") + words.drop(1)).joinToString(" ")
        }
    }
}
