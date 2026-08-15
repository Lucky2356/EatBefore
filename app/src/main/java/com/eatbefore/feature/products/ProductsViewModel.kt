package com.eatbefore.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.R
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.repository.ProductRepository
import com.eatbefore.domain.usecase.DeleteProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One product card as the catalogue screen shows it. */
data class ProductRowUi(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    /** Packages at home right now. Above zero, the card cannot be struck off. */
    val presentBatches: Int,
)

data class ProductsUiState(
    val isLoading: Boolean = true,
    val rows: List<ProductRowUi> = emptyList(),
    /** True when the catalogue is empty in itself, rather than filtered down to nothing. */
    val isEmpty: Boolean = false,
)

/**
 * The catalogue of product cards, and the one place they can be struck off.
 *
 * Deleting is offered here rather than on the product card: what makes the list annoying
 * is a wrong or duplicate card lingering among the suggestions, and that is a job of
 * tidying several at once — not something reached through one package in the fridge.
 */
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val deleteProduct: DeleteProductUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    private val _message = MutableStateFlow<ProductsMessage?>(null)
    val message: StateFlow<ProductsMessage?> = _message.asStateFlow()

    val uiState: StateFlow<ProductsUiState> = combine(
        productRepository.observeActive(),
        productRepository.observePresentCounts(),
        query,
    ) { products, counts, q ->
        val rows = products
            .filter { it.matches(q) }
            .map { product ->
                ProductRowUi(
                    id = product.id,
                    name = product.name,
                    brand = product.brand,
                    barcode = product.barcode,
                    presentBatches = counts[product.id] ?: 0,
                )
            }
        ProductsUiState(isLoading = false, rows = rows, isEmpty = products.isEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ProductsUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    /** Whether striking this card off is allowed — nothing of it is at home. */
    fun canDelete(row: ProductRowUi): Boolean = row.presentBatches == 0

    fun delete(row: ProductRowUi) {
        viewModelScope.launch {
            _message.value = when (val result = deleteProduct(row.id)) {
                is DeleteProductUseCase.Result.Deleted ->
                    // The card is only marked, so offering it back costs nothing and is
                    // the difference between a tidy-up and a decision the user must be
                    // sure about before tapping.
                    ProductsMessage(R.string.products_deleted, undoProductId = row.id)

                is DeleteProductUseCase.Result.StillInStock ->
                    ProductsMessage(R.string.products_delete_in_stock, count = result.batches)

                DeleteProductUseCase.Result.NotFound ->
                    ProductsMessage(R.string.products_delete_gone)
            }
        }
    }

    fun undoDelete(productId: Long) {
        viewModelScope.launch {
            deleteProduct.restore(productId)
            _message.value = null
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun Product.matches(q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim().lowercase()
        return name.lowercase().contains(needle) ||
            brand?.lowercase()?.contains(needle) == true ||
            barcode?.contains(needle) == true
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Something to tell the user, once. [undoProductId] is set only when the action can be
 * taken back, which is what turns the snackbar's button on.
 */
data class ProductsMessage(val textRes: Int, val count: Int = 0, val undoProductId: Long? = null)
