package com.eatbefore.domain.model

/**
 * What one batch cost. Separate from [InventoryBatch] because analytics needs the money of
 * many batches without the rest of them.
 *
 * The currency travels with the amount rather than being an app-wide setting: a jar bought
 * on holiday stays priced in euros, and a total that silently mixed currencies would be a
 * number with no meaning.
 */
data class BatchPrice(val amount: Double, val currency: String?)
