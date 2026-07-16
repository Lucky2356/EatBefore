package com.eatbefore.domain.usecase

import com.eatbefore.core.common.validation.InputValidator
import com.eatbefore.domain.model.Product
import com.eatbefore.domain.repository.ProductRepository
import javax.inject.Inject

/**
 * Prevents duplicate product cards. Given a barcode or a name+brand, returns an existing
 * matching card so a new addition reuses it (its batches accumulate under one product)
 * instead of creating a near-identical duplicate.
 *
 * Barcode match takes priority; otherwise a case-insensitive name+brand match among
 * user-created products is used. Returns null when nothing matches.
 */
class MergeSameProductUseCase @Inject constructor(private val productRepository: ProductRepository) {

    suspend fun findDuplicate(name: String, brand: String?, barcode: String? = null): Product? {
        val cleanBarcode = InputValidator.sanitizeBarcode(barcode)
        if (cleanBarcode != null) {
            productRepository.getByBarcode(cleanBarcode)?.let { return it }
        }
        val cleanName = InputValidator.sanitizeText(name, InputValidator.MAX_NAME_LENGTH)
            ?: return null
        val cleanBrand = InputValidator.sanitizeText(brand, InputValidator.MAX_BRAND_LENGTH)
        return productRepository.findUserProductByNameAndBrand(cleanName, cleanBrand)
    }
}
