package com.midnightdancer.feature.trainer.logic

import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem

/** A selected item may expose the form even when its saved audio cannot produce a plan. */
data class RehearsalSelection(
    val choreographyId: String,
    val title: String,
    val durationMs: Long? = null,
    val planResult: RehearsalPlanResult,
    val canConfigure: Boolean,
)

/** Resolves only the selected vault ID; display names and other items are never audio fallbacks. */
object RehearsalSelectionResolver {
    fun resolve(
        selectedId: String,
        items: List<ChoreographyVaultItem>,
    ): RehearsalSelection {
        if (selectedId.isBlank()) {
            return RehearsalSelection(
                choreographyId = selectedId,
                title = "",
                planResult = RehearsalPlanResult(error = "Сначала выберите хореографию"),
                canConfigure = false,
            )
        }
        val item = items.firstOrNull { it.id == selectedId }
            ?: return RehearsalSelection(
                choreographyId = selectedId,
                title = "",
                planResult = RehearsalPlanResult(error = "Выбранная хореография недоступна"),
                canConfigure = false,
            )
        val result = item.snapshot?.let { RehearsalPlanBuilder.fromSnapshot(item.id, it) }
            ?: RehearsalPlanResult(error = "Для запуска добавьте музыку и сохраните эту хореографию")
        return RehearsalSelection(
            choreographyId = item.id,
            title = item.title,
            durationMs = result.plan?.durationMs,
            planResult = result,
            canConfigure = true,
        )
    }
}
