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
)

/** A product the household buys regularly, offered for one-tap re-adding. */
data class FrequentProductUi(val productId: Long, val name: String)

data class ShoppingUiState(
    val isLoading: Boolean = true,
    /** Rows grouped by category, categories sorted, unbought items first inside a group. */
    val groups: List<Pair<String?, List<ShoppingRowUi>>> = emptyList(),
    /** Repeat purchases not already on the list. */
    val frequent: List<FrequentProductUi> = emptyList(),
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    shoppingListRepository: ShoppingListRepository,
    productRepository: ProductRepository,
    private val addToShoppingList: AddToShoppingListUseCase,
    private val toggleItem: ToggleShoppingItemUseCase,
    private val deleteItem: DeleteShoppingItemUseCase,
    private val moveToInventory: MoveShoppingItemToInventoryUseCase,
) : ViewModel() {

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

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
                .onSuccess { _message.value = com.eatbefore.R.string.shopping_added }
        }
    }

    fun toggle(id: Long) {
        viewModelScope.launch { runCatching { toggleItem(id) } }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { deleteItem(id) } }
    }

    fun moveToStock(id: Long) {
        viewModelScope.launch {
            runCatching { moveToInventory(id) }
                .onSuccess { batchId ->
                    if (batchId != null) _message.value = com.eatbefore.R.string.shopping_moved
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
