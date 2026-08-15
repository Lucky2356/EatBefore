package com.eatbefore.domain.usecase

import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Strikes a product card off the catalogue, so it stops being offered among frequent
 * purchases and in the lists to choose from.
 *
 * The card is marked, not removed. Every batch and every event ever recorded under it
 * still points here, and deleting the row would leave the history with rows it cannot
 * name. The mark also has to travel: to the other phone a row that simply vanished looks
 * like a row it has and we don't, and the next exchange would hand it straight back —
 * which is exactly what used to happen (TODO.md, "удаление продукта не доходит до соседа").
 */
class DeleteProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) {

    sealed interface Result {
        data object Deleted : Result

        /** Refused: [batches] packages are still at home. Deleting would hide real food. */
        data class StillInStock(val batches: Int) : Result

        data object NotFound : Result
    }

    suspend operator fun invoke(productId: Long): Result {
        productRepository.getById(productId) ?: return Result.NotFound
        // Refusing rather than cascading. Striking off a card whose packages are in the
        // fridge would take food out of the stock list, and the user asked to tidy the
        // catalogue, not to write anything off.
        val present = inventoryRepository.observeAllForProduct(productId).first()
            .count { it.batch.status.isPresent && it.batch.deletedAt == null }
        if (present > 0) return Result.StillInStock(present)

        productRepository.setDeleted(productId, deleted = true)
        return Result.Deleted
    }

    /** Puts a struck-off card back — used when the same thing is bought again. */
    suspend fun restore(productId: Long) {
        productRepository.setDeleted(productId, deleted = false)
    }
}
