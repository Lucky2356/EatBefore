package com.eatbefore.feature.scanner.camera

import com.eatbefore.domain.model.BarcodeType
import com.google.mlkit.vision.barcode.common.Barcode

/** Maps an ML Kit barcode format constant to the domain [BarcodeType]. */
fun mlkitFormatToBarcodeType(format: Int): BarcodeType = when (format) {
    Barcode.FORMAT_EAN_13 -> BarcodeType.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeType.EAN_8
    Barcode.FORMAT_UPC_A -> BarcodeType.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeType.UPC_E
    Barcode.FORMAT_QR_CODE -> BarcodeType.QR
    Barcode.FORMAT_DATA_MATRIX -> BarcodeType.DATA_MATRIX
    Barcode.FORMAT_CODE_128 -> BarcodeType.CODE_128
    else -> BarcodeType.OTHER
}
