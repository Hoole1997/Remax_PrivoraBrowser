package com.example.browser.utils

import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.Utils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import net.corekit.core.report.ReportDataManager

class GoogleBarcodeScanner {

    fun scanBarcode(scannerResult: (String) -> Unit) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .enableAutoZoom()
                .build()
            val scanner = GmsBarcodeScanning.getClient(Utils.getApp(), options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue: String = barcode.rawValue ?: return@addOnSuccessListener
                    scannerResult(rawValue)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    // ...
                }
                .addOnCompleteListener {

                }
        } catch (e: Exception) {
            ReportDataManager.reportData("scan_barcode_error", mapOf("error" to "${e.message}"))
        }

    }

}