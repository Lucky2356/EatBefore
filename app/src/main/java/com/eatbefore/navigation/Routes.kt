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
    const val ADD_MANUAL_ARG_EXPIRY = "expiryEpochDay"
    const val ADD_MANUAL = "add_manual?$ADD_MANUAL_ARG_BARCODE={$ADD_MANUAL_ARG_BARCODE}" +
        "&$ADD_MANUAL_ARG_EXPIRY={$ADD_MANUAL_ARG_EXPIRY}"

    /** [expiryEpochDay] carries a date extracted from a scanned GS1 code, if any. */
    fun addManual(barcode: String? = null, expiryEpochDay: Long? = null): String {
        val params = buildList {
            if (!barcode.isNullOrBlank()) add("$ADD_MANUAL_ARG_BARCODE=$barcode")
            if (expiryEpochDay != null) add("$ADD_MANUAL_ARG_EXPIRY=$expiryEpochDay")
        }
        return if (params.isEmpty()) "add_manual" else "add_manual?" + params.joinToString("&")
    }

    const val PRODUCT_BATCH_ARG = "batchId"
    const val PRODUCT = "product/{$PRODUCT_BATCH_ARG}"
    fun product(batchId: Long) = "product/$batchId"

    const val HISTORY = "history"

    const val SETTINGS = "settings"
    const val SETTINGS_LOCATIONS = "settings/locations"
    const val ANALYTICS = "analytics"

    const val OCR = "ocr_expiry"

    /**
     * The camera opened to answer "is this already at home?" — it hands the code back to
     * the stock list instead of adding anything. A route of its own rather than an
     * argument on [SCANNER]: that one is a tab, and giving a tab a mode would leave the
     * bottom bar highlighted while the screen does something else entirely.
     */
    const val SCAN_LOOKUP = "scan_lookup"

    /** Key used to hand a scanned barcode back to the stock search. */
    const val SCAN_LOOKUP_RESULT = "scan_lookup_result"

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
