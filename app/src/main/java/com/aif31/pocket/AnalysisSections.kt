package com.aif31.pocket

/**
 * The breakdowns on the Análisis screen, in their default order. The person can reorder them; none can be hidden.
 * [availability] says when a section appears only some of the time.
 */
internal enum class AnalysisSection(val title: String, val availability: String? = null) {
    TREND("Tendencia del gasto diario"),
    POCKETS("Por Pocket"),
    RECURRING("Cargos recurrentes", "Solo en el periodo en curso"),
    MERCHANTS("Comercios principales"),
    WHEN("Cuándo gastas"),
    PAYMENT_METHODS("Métodos de pago"),
    FOREIGN_CURRENCY("Moneda extranjera"),
    ROLLOVER("Rollover acumulado", "Solo si algún Pocket usa rollover"),
    ;

    companion object {
        /**
         * The sections named in [saved], in that order, then any it leaves out in their default order. Unknown names,
         * such as a section a later version removed, are skipped.
         */
        fun ordered(saved: List<String>): List<AnalysisSection> {
            val chosen = saved.mapNotNull { name -> entries.firstOrNull { it.name == name } }.distinct()
            return chosen + entries.filterNot { it in chosen }
        }
    }
}

/** This order with [section] moved [offset] places, stopping at either end. */
internal fun List<AnalysisSection>.moved(section: AnalysisSection, offset: Int): List<AnalysisSection> {
    val from = indexOf(section)
    if (from < 0) return this
    val to = (from + offset).coerceIn(0, lastIndex)
    return toMutableList().apply { add(to, removeAt(from)) }
}
