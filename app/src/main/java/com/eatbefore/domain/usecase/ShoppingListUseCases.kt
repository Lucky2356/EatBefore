package com.eatbefore.domain.usecase

import com.eatbefore.core.common.time.AppClock
import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.EventType
import com.eatbefore.domain.model.InventoryEvent
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.model.ShoppingListItem
import com.eatbefore.domain.model.ShoppingPriority
import com.eatbefore.domain.repository.HistoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.ShoppingListRepository
import com.eatbefore.domain.repository.StorageLocationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Adds an item to the shopping list — either linked to an existing product (typically when
 * a batch runs out) or as a free-typed name. When it originates from a batch, an
 * ADDED_TO_SHOPPING_LIST event is appended so the product's history stays complete.
 *
 * Re-adding a product that is already on the list (and not yet bought) bumps its quantity
 * instead of creating a duplicate row.
 */
class AddToShoppingListUseCase @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val historyRepository: HistoryRepository,
    private val clock: AppClock,
) {

    data class Params(
        val productId: Long? = null,
        val customName: String? = null,
        val quantity: Double = 1.0,
        val measurementUnit: MeasurementUnit = MeasurementUnit.PIECE,
        val priority: ShoppingPriority = ShoppingPriority.NORMAL,
        /** Batch this came from, if the user wrote it off. Enables the history event. */
        val sourceInventoryBatchId: Long? = null,
    )

    suspend operator fun invoke(params: Params): Long {
        require(params.productId != null || !params.customName.isNullOrBlank()) {
            "Either productId or customName is required"
        }
        // Free-typed entries become product names when moved to stock, so capitalize here too.
        val name = params.customName
            ?.let { InputValidator.normalizeProductName(it, field = "customName") }
        val quantity = InputValidator.clampQuantity(params.quantity).coerceAtLeast(1.0)
        val now = clock.now()

        // Merge with an existing open entry for the same product instead of duplicating.
        // A typed name is matched too, whatever its capitals: «молоко» written twice used to
        // become two rows, and a list pasted from a chat repeats things all the time.
        val existing = if (params.productId != null) {
            shoppingListRepository.findOpenForProduct(params.productId)
        } else {
            shoppingListRepository.observeAll().first().firstOrNull { item ->
                !item.isCompleted && item.productId == null && item.customName.equals(name, ignoreCase = true)
            }
        }
        if (existing != null) {
            val merged = existing.copy(quantity = existing.quantity + quantity)
            shoppingListRepository.upsert(merged)
            recordEventIfSourced(params, now)
            return merged.id
        }

        val id = shoppingListRepository.upsert(
            ShoppingListItem(
                productId = params.productId,
                customName = if (params.productId == null) name else null,
                quantity = quantity,
                measurementUnit = params.measurementUnit,
                priority = params.priority,
                isCompleted = false,
                addedAt = now,
                sourceInventoryBatchId = params.sourceInventoryBatchId,
            ),
        )
        recordEventIfSourced(params, now)
        return id
    }

    private suspend fun recordEventIfSourced(params: Params, now: java.time.Instant) {
        val batchId = params.sourceInventoryBatchId ?: return
        val productId = params.productId ?: return
        historyRepository.record(
            InventoryEvent(
                inventoryBatchId = batchId,
                productId = productId,
                eventType = EventType.ADDED_TO_SHOPPING_LIST,
                createdAt = now,
            ),
        )
    }
}

/** Toggles the "bought" checkbox on a shopping item. */
class ToggleShoppingItemUseCase @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(itemId: Long) {
        val item = shoppingListRepository.getById(itemId) ?: return
        val completed = !item.isCompleted
        shoppingListRepository.upsert(
            item.copy(
                isCompleted = completed,
                completedAt = if (completed) clock.now() else null,
            ),
        )
    }
}

/** Removes an item from the shopping list. */
class DeleteShoppingItemUseCase @Inject constructor(private val shoppingListRepository: ShoppingListRepository) {
    suspend operator fun invoke(itemId: Long) {
        val item = shoppingListRepository.getById(itemId) ?: return
        shoppingListRepository.delete(item)
    }
}

/**
 * "Bought it" — turns a shopping item back into stock: adds a batch for the linked product
 * (or creates a product card from the free-typed name), then removes the shopping entry.
 */
class MoveShoppingItemToInventoryUseCase @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val storageLocationRepository: StorageLocationRepository,
    private val productRepository: ProductRepository,
    private val addBatch: AddBatchUseCase,
    private val addManualProduct: AddManualProductUseCase,
) {

    suspend operator fun invoke(itemId: Long, storageLocationId: Long? = null): Long? {
        val item = shoppingListRepository.getById(itemId) ?: return null
        val locationId = storageLocationId
            ?: storageLocationRepository.getDefault()?.id
            ?: return null

        val batchId = when {
            item.productId != null && productRepository.getById(item.productId) != null ->
                addBatch(
                    AddBatchUseCase.Params(
                        productId = item.productId,
                        storageLocationId = locationId,
                        quantity = item.quantity,
                        measurementUnit = item.measurementUnit,
                    ),
                )

            !item.customName.isNullOrBlank() ->
                addManualProduct(
                    AddManualProductUseCase.Params(
                        name = item.customName,
                        storageLocationId = locationId,
                        quantity = item.quantity,
                        measurementUnit = item.measurementUnit,
                    ),
                )

            else -> return null
        }

        shoppingListRepository.delete(item)
        return batchId
    }
}
