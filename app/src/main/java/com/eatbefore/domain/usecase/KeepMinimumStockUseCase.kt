package com.eatbefore.domain.usecase

import com.eatbefore.domain.repository.InventoryRepository
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.ShoppingListRepository
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Puts a product onto the shopping list once what is left at home drops below the minimum
 * set on its card ([com.eatbefore.domain.model.Product.minQuantity]).
 *
 * Called after anything that takes stock away — a decrease, «закончилось», a write-off —
 * from inside those use cases, so every path counts: the card, the lists, the button in
 * the notification. It asks for what is missing up to the minimum, never less than one.
 *
 * Only when the product is not on the list already: a second decrease must not keep
 * bumping the amount, and an entry the user deleted stays deleted until stock falls again.
 */
class KeepMinimumStockUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val addToShoppingList: AddToShoppingListUseCase,
) {

    /** True when the product was put on the list. */
    suspend operator fun invoke(productId: Long): Boolean {
        val product = productRepository.getById(productId)?.takeIf { it.deletedAt == null } ?: return false
        val minimum = product.minQuantity?.takeIf { it > 0.0 } ?: return false
        val atHome = inventoryRepository.getPresentForProduct(productId).sumOf { it.quantity }
        val needed = atHome < minimum && shoppingListRepository.findOpenForProduct(productId) == null
        if (needed) {
            addToShoppingList(
                AddToShoppingListUseCase.Params(
                    productId = productId,
                    quantity = ceil(minimum - atHome).coerceAtLeast(1.0),
                    measurementUnit = product.measurementUnit,
                ),
            )
        }
        return needed
    }
}
