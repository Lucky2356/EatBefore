package com.eatbefore.navigation

/** Central definition of navigation routes. Kept as plain strings for Navigation Compose. */
object Routes {
    const val ONBOARDING = "onboarding"

    const val HOME = "home"
    const val INVENTORY = "inventory"
    const val SCANNER = "scanner"
    const val SHOPPING = "shopping"
    const val MORE = "more"

    const val ADD_MANUAL_ARG_BARCODE = "barcode"
    const val ADD_MANUAL = "add_manual?$ADD_MANUAL_ARG_BARCODE={$ADD_MANUAL_ARG_BARCODE}"
    fun addManual(barcode: String? = null): String =
        if (barcode.isNullOrBlank()) "add_manual" else "add_manual?$ADD_MANUAL_ARG_BARCODE=$barcode"

    const val PRODUCT_BATCH_ARG = "batchId"
    const val PRODUCT = "product/{$PRODUCT_BATCH_ARG}"
    fun product(batchId: Long) = "product/$batchId"

    const val HISTORY = "history"

    const val SETTINGS = "settings"

    const val OCR = "ocr_expiry"
    /** Key used to hand a recognized expiry date (epoch day) back to the add screen. */
    const val OCR_RESULT_EPOCH_DAY = "ocr_result_epoch_day"
}

/** Top-level destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(val route: String) {
    HOME(Routes.HOME),
    INVENTORY(Routes.INVENTORY),
    SCANNER(Routes.SCANNER),
    SHOPPING(Routes.SHOPPING),
    MORE(Routes.MORE),
}
