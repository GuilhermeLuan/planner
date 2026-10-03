package dev.guilhermeluan.planner.tasks

/** Regra do Estoque: quanto uma mudança de estado de Dose desconta ou devolve. */
object StockLedger {
    /** Quanto sai do Estoque ao tomar a Dose; nunca mais do que resta. */
    private fun deduction(stock: Int, doseAmount: Int): Int = minOf(stock, doseAmount)

    /** Estoque depois de a Dose passar de [previous] para [next], e o quanto ela passa a ter descontado. */
    fun transition(
        stock: Int,
        doseAmount: Int,
        previous: DoseStatus?,
        previousDeducted: Int,
        next: DoseStatus,
    ): Result = when {
        next == DoseStatus.TAKEN && previous != DoseStatus.TAKEN -> {
            val deducted = deduction(stock, doseAmount)
            Result(stock - deducted, deducted)
        }
        next != DoseStatus.TAKEN && previous == DoseStatus.TAKEN -> Result(stock + previousDeducted, 0)
        else -> Result(stock, if (previous == DoseStatus.TAKEN) previousDeducted else 0)
    }

    data class Result(val stock: Int, val deducted: Int)
}
