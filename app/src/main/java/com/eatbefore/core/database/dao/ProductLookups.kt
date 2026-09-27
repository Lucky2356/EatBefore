package com.eatbefore.core.database.dao

import com.eatbefore.core.database.entity.ProductEntity
import com.eatbefore.domain.model.HomemadeKind

/**
 * Case-insensitive lookup used when merging duplicate manual entries. Bought food only: a
 * jar of home-made jam and a bought one share a name, and folding one into the other would
 * give it the wrong label and the wrong shelf life.
 *
 * Compared here rather than in SQL: SQLite's LOWER() is only guaranteed to fold ASCII, so
 * «Огурцы маринованные» typed twice with a different capital letter became two cards.
 */
suspend fun ProductDao.findUserProductByNameAndBrand(name: String, brand: String?): ProductEntity? =
    findWithoutBarcodeOfNameLength(name).firstOrNull {
        it.homemadeKind == null &&
            it.name.equals(name, ignoreCase = true) &&
            (if (brand == null) it.brand == null else it.brand.equals(brand, ignoreCase = true))
    }

/** The home-made counterpart: the same soup cooked again reuses its card. */
suspend fun ProductDao.findHomemadeProductByName(name: String, kind: HomemadeKind): ProductEntity? =
    findWithoutBarcodeOfNameLength(name).firstOrNull {
        it.homemadeKind == kind && it.name.equals(name, ignoreCase = true)
    }
