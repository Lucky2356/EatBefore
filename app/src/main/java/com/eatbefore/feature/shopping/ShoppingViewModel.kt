package com.eatbefore.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.domain.model.MeasurementUnit
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.repository.ShoppingListRepository
import com.eatbefore.domain.usecase.AddToShoppingListUseCase
import com.eatbefore.domain.usecase.DeleteShoppingItemUseCase
import com.eatbefore.domain.usecase.MoveShoppingItemToInventoryUseCase
import com.eatbefore.domain.usecase.ToggleShoppingItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the shopping list, already resolved for display. */
data class ShoppingRowUi(
    val id: Long,
    val title: String,
    val quantity: Double,
    val unit: MeasurementUnit,
    /** Null means "no category"; the screen groups by this. */
    val category: String?,
    val isCompleted: Boolean,
    /**
     * A free-typed entry, whose name is the user's own text. Rows backed by a product show
     * that product's name, and editing it here must not quietly rename the product
     * everywhere else it appears.
     */
    val isCustom: Boolean,
)

/** A product the household buys regularly, offered for one-tap re-adding. */
data class FrequentProductUi(val productId: Long, val name: String)

/**
 * A one-shot snackbar message. [id] increments so the same message twice in a row still
 * shows twice; [undoable] adds the Undo action.
 */
data class ShoppingMessage(@androidx.annotation.StringRes val textRes: Int, val undoable: Boolean, val id: Long)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    /** Rows grouped by category, categories sorted, unbought items first inside a group. */
    val groups: List<Pair<String?, List<ShoppingRowUi>>> = emptyList(),
    /** Repeat purchases not already on the list. */
    val frequent: List<FrequentProductUi> = emptyList(),
    /** Whether anything is ticked off, i.e. whether there is anything to clear. */
    val hasCompleted: Boolean = false,
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    productRepository: ProductRepository,
    private val addToShoppingList: AddToShoppingListUseCase,
    private val toggleItem: ToggleShoppingItemUseCase,
    private val deleteItem: DeleteShoppingItemUseCase,
    private val moveToInventory: MoveShoppingItemToInventoryUseCase,
) : ViewModel() {

    private val _message = MutableStateFlow<ShoppingMessage?>(null)
    val message: StateFlow<ShoppingMessage?> = _message.asStateFlow()

    /**
     * The rows removed by the last deletion, held only until its snackbar is answered or
     * expires. A list because "clear bought" removes many at once, and undoing half of
     * that would be worse than not offering undo at all.
     */
    private var deletedItems: List<com.eatbefore.domain.model.ShoppingListItem> = emptyList()
    private var messageCounter = 0L

    val uiState: StateFlow<ShoppingUiState> = combine(
        shoppingListRepository.observeAll(),
        productRepository.observeAll(),
        productRepository.observeFrequent(),
    ) { items, products, frequent ->
        val byId = products.associateBy { it.id }
        val rows = items.map { item ->
            val product = item.productId?.let(byId::get)
            ShoppingRowUi(
                id = item.id,
                title = product?.name ?: item.customName.orEmpty(),
                quantity = item.quantity,
                unit = item.measurementUnit,
                category = product?.category,
                isCompleted = item.isCompleted,
                isCustom = item.productId == null,
            )
        }
        val groups = rows
            .groupBy { it.category }
            .toList()
            .sortedWith(compareBy(nullsLast()) { it.first?.lowercase() })
            .map { (category, groupRows) ->
                category to groupRows.sortedWith(
                    compareBy<ShoppingRowUi> { it.isCompleted }.thenBy { it.title.lowercase() },
                )
            }
        // Don't suggest what is already on the list.
        val onList = items.filter { !it.isCompleted }.mapNotNull { it.productId }.toSet()
        ShoppingUiState(
            isLoading = false,
            groups = groups,
            hasCompleted = items.any { it.isCompleted },
            frequent = frequent
                .filter { it.id !in onList }
                .map { FrequentProductUi(productId = it.id, name = it.name) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingUiState(),
    )

    fun addManual(name: String, quantity: Double, unit: MeasurementUnit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                addToShoppingList(
                    AddToShoppingListUseCase.Params(
                        customName = name,
                        quantity = quantity,
                        measurementUnit = unit,
                    ),
                )
            }
        }
    }

    /** One-tap re-add of a regular purchase. */
    fun addFrequent(productId: Long) {
        viewModelScope.launch {
            runCatching { addToShoppingList(AddToShoppingListUseCase.Params(productId = productId)) }
                .onSuccess { notify(com.eatbefore.R.string.shopping_added) }
        }
    }

    fun toggle(id: Long) {
        viewModelScope.launch { runCatching { toggleItem(id) } }
    }

    /**
     * Deleting is one tap next to the "bought it" button, and the list is where the user
     * moves fastest — so the removed row is kept until the snackbar goes away and can be
     * put back exactly as it was.
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            val removed = shoppingListRepository.getById(id)
            runCatching { deleteItem(id) }.onSuccess {
                deletedItems = listOfNotNull(removed)
                notify(com.eatbefore.R.string.shopping_deleted, undoable = removed != null)
            }
        }
    }

    /** Tidies the list after a shop without touching what is still to buy. */
    fun clearCompleted() {
        viewModelScope.launch {
            val completed = shoppingListRepository.observeAll().first().filter { it.isCompleted }
            if (completed.isEmpty()) return@launch
            runCatching { completed.forEach { shoppingListRepository.delete(it) } }.onSuccess {
                deletedItems = completed
                notify(com.eatbefore.R.string.shopping_cleared, undoable = true)
            }
        }
    }

    fun undoDelete() {
        val removed = deletedItems
        if (removed.isEmpty()) return
        deletedItems = emptyList()
        viewModelScope.launch {
            runCatching { removed.forEach { shoppingListRepository.upsert(it) } }
        }
    }

    /**
     * Corrects a row in place. The name only applies to free-typed entries — a row backed
     * by a product carries that product's name, and renaming it here would rename it in
     * the inventory too.
     */
    fun updateItem(id: Long, name: String, quantity: Double, unit: MeasurementUnit) {
        viewModelScope.launch {
            val item = shoppingListRepository.getById(id) ?: return@launch
            val edited = item.copy(
                customName = if (item.productId == null) name.trim().ifBlank { item.customName } else item.customName,
                quantity = quantity.coerceAtLeast(1.0),
                measurementUnit = unit,
            )
            runCatching { shoppingListRepository.upsert(edited) }
        }
    }

    fun moveToStock(id: Long) {
        viewModelScope.launch {
            runCatching { moveToInventory(id) }
                .onSuccess { batchId ->
                    if (batchId != null) notify(com.eatbefore.R.string.shopping_moved)
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * The list as plain text, for handing to whoever is going to the shop.
     *
     * A pure function taking its localized pieces as arguments rather than reaching for
     * resources itself: this is the part worth a test, and a test cannot run a composable.
     * Bought items are left out — what is sent is what still needs buying.
     */
    fun buildShareText(title: String, unitLabel: (MeasurementUnit) -> String): String {
        val lines = uiState.value.groups
            .flatMap { (_, rows) -> rows }
            .filterNot { it.isCompleted }
            .map { row ->
                val amount = if (row.quantity % 1.0 == 0.0) {
                    row.quantity.toLong().toString()
                } else {
                    row.quantity.toString()
                }
                "— ${row.title}, $amount ${unitLabel(row.unit)}"
            }
        return if (lines.isEmpty()) title else (listOf(title) + lines).joinToString("\n")
    }

    private fun notify(textRes: Int, undoable: Boolean = false) {
        _message.value = ShoppingMessage(textRes, undoable, ++messageCounter)
    }
}
