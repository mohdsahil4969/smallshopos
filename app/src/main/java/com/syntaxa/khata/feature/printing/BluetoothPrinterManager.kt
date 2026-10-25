package com.syntaxa.khata.feature.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.domain.model.BillWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed class PrintResult {
    data object Success : PrintResult()
    data class Failure(val message: String) : PrintResult()
}

data class DiscoveredPrinter(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

class BluetoothPrinterManager(private val context: Context) {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val WIDTH_58MM = 32
        const val WIDTH_80MM = 48

        // ESC/POS Command bytes
        private val CMD_INIT = byteArrayOf(0x1B, 0x40)
        private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        private val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        private val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        private val CMD_LARGE_TEXT = byteArrayOf(0x1D, 0x21, 0x11)
        private val CMD_NORMAL_TEXT = byteArrayOf(0x1D, 0x21, 0x00)
        private val CMD_FEED_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x03)
        private val CMD_LINE_FEED = byteArrayOf(0x0A)
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Throwable) {
            null
        }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun getDiscoveredPrinters(): List<DiscoveredPrinter> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.mapNotNull { dev ->
                try {
                    val name = dev.name ?: "Bluetooth Printer"
                    val address = dev.address ?: ""
                    DiscoveredPrinter(name = name, address = address, device = dev)
                } catch (_: Throwable) {
                    null
                }
            } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        return getDiscoveredPrinters().map { it.device }
    }

    @SuppressLint("MissingPermission")
    suspend fun printTestReceipt(
        device: BluetoothDevice,
        shop: ShopProfileEntity,
        paperWidth: Int = WIDTH_58MM
    ): PrintResult = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket.connect()

            val outputStream: OutputStream = socket.outputStream

            outputStream.write(CMD_INIT)
            outputStream.write(CMD_ALIGN_CENTER)
            outputStream.write(CMD_BOLD_ON)
            outputStream.write(CMD_LARGE_TEXT)
            val printShopName = shop.shopName.ifBlank { "Syntaxa Khata" }
            outputStream.write("${printShopName}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_NORMAL_TEXT)
            outputStream.write(CMD_BOLD_OFF)

            if (shop.address.isNotBlank()) {
                outputStream.write("${shop.address}\n".toByteArray(Charsets.US_ASCII))
            }
            if (shop.phone.isNotBlank()) {
                outputStream.write("Tel: ${shop.phone}\n".toByteArray(Charsets.US_ASCII))
            }

            outputStream.write(separatorLine(paperWidth).toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_ALIGN_CENTER)
            outputStream.write(CMD_BOLD_ON)
            outputStream.write("*** TEST RECEIPT ***\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Printer Connected OK!\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_BOLD_OFF)
            outputStream.write(CMD_ALIGN_LEFT)
            outputStream.write("Date: ${DateTimeUtils.formatDateTime(System.currentTimeMillis())}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Paper Width: $paperWidth chars\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(separatorLine(paperWidth).toByteArray(Charsets.US_ASCII))

            outputStream.write(CMD_ALIGN_CENTER)
            if (shop.receiptFooter.isNotBlank()) {
                outputStream.write("${shop.receiptFooter}\n".toByteArray(Charsets.US_ASCII))
            }
            outputStream.write("Powered by Syntaxa Khata\n\n\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_FEED_CUT)
            outputStream.flush()

            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Failure(e.localizedMessage ?: "Failed to connect to printer")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) { }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printBillReceipt(
        device: BluetoothDevice,
        shop: ShopProfileEntity,
        billWithItems: BillWithItems,
        paperWidth: Int = WIDTH_58MM
    ): PrintResult = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket.connect()

            val outputStream: OutputStream = socket.outputStream
            val bill = billWithItems.bill
            val items = billWithItems.items
            val customer = billWithItems.customer

            outputStream.write(CMD_INIT)
            outputStream.write(CMD_ALIGN_CENTER)
            outputStream.write(CMD_BOLD_ON)
            outputStream.write(CMD_LARGE_TEXT)
            val printShopName = shop.shopName.ifBlank { "Syntaxa Khata" }
            outputStream.write("${printShopName}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_NORMAL_TEXT)
            outputStream.write(CMD_BOLD_OFF)

            if (shop.address.isNotBlank()) outputStream.write("${shop.address}\n".toByteArray(Charsets.US_ASCII))
            if (shop.phone.isNotBlank()) outputStream.write("Tel: ${shop.phone}\n".toByteArray(Charsets.US_ASCII))

            outputStream.write(separatorLine(paperWidth).toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_ALIGN_LEFT)
            outputStream.write("Bill No: ${bill.receiptNumber}\n".toByteArray(Charsets.US_ASCII))
            outputStream.write("Date: ${DateTimeUtils.formatDateTime(bill.createdAt)}\n".toByteArray(Charsets.US_ASCII))
            if (customer != null) {
                outputStream.write("Customer: ${customer.name}\n".toByteArray(Charsets.US_ASCII))
            } else {
                outputStream.write("Customer: Cash Walk-in\n".toByteArray(Charsets.US_ASCII))
            }
            outputStream.write(separatorLine(paperWidth).toByteArray(Charsets.US_ASCII))

            // Column Header
            outputStream.write(CMD_BOLD_ON)
            outputStream.write(formatColumns("Item", "Qty", "Total", paperWidth).toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_BOLD_OFF)
            outputStream.write(separatorLine(paperWidth, '-').toByteArray(Charsets.US_ASCII))

            if (items.isNotEmpty()) {
                for (item in items) {
                    val line = formatColumns(
                        item.description.take(paperWidth - 16),
                        String.format("%.1f", item.quantity),
                        CurrencyFormatter.formatPaise(item.lineTotalPaise, showSymbol = false),
                        paperWidth
                    )
                    outputStream.write(line.toByteArray(Charsets.US_ASCII))
                }
            } else {
                val desc = if (bill.notes.isNotBlank()) bill.notes else "General Goods"
                val line = formatColumns(desc.take(paperWidth - 16), "1", CurrencyFormatter.formatPaise(bill.totalPaise, showSymbol = false), paperWidth)
                outputStream.write(line.toByteArray(Charsets.US_ASCII))
            }

            outputStream.write(separatorLine(paperWidth, '-').toByteArray(Charsets.US_ASCII))
            outputStream.write(formatRowRight("Subtotal: ", CurrencyFormatter.formatPaise(bill.subtotalPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
            if (bill.discountPaise > 0) {
                outputStream.write(formatRowRight("Discount: ", "-${CurrencyFormatter.formatPaise(bill.discountPaise, showSymbol = false)}", paperWidth).toByteArray(Charsets.US_ASCII))
            }
            outputStream.write(CMD_BOLD_ON)
            outputStream.write(formatRowRight("TOTAL BILL: ", CurrencyFormatter.formatPaise(bill.totalPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_BOLD_OFF)
            if (bill.paymentMethod == "SPLIT") {
                outputStream.write(formatRowRight("Paid (Split): ", CurrencyFormatter.formatPaise(bill.amountPaidPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
                outputStream.write(formatRowRight(" - Cash: ", CurrencyFormatter.formatPaise(bill.cashPaidPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
                outputStream.write(formatRowRight(" - UPI: ", CurrencyFormatter.formatPaise(bill.upiPaidPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
            } else {
                outputStream.write(formatRowRight("Paid (${bill.paymentMethod}): ", CurrencyFormatter.formatPaise(bill.amountPaidPaise, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
            }

            val remaining = bill.totalPaise - bill.amountPaidPaise
            if (remaining > 0) {
                outputStream.write(CMD_BOLD_ON)
                outputStream.write(formatRowRight("Balance Udhaar: ", CurrencyFormatter.formatPaise(remaining, showSymbol = false), paperWidth).toByteArray(Charsets.US_ASCII))
                outputStream.write(CMD_BOLD_OFF)
            }

            outputStream.write(separatorLine(paperWidth).toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_ALIGN_CENTER)
            if (shop.receiptFooter.isNotBlank()) {
                outputStream.write("${shop.receiptFooter}\n".toByteArray(Charsets.US_ASCII))
            }
            outputStream.write("Syntaxa Khata\n\n\n".toByteArray(Charsets.US_ASCII))
            outputStream.write(CMD_FEED_CUT)
            outputStream.flush()

            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Failure(e.localizedMessage ?: "Failed to print bill")
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) { }
        }
    }

    private fun separatorLine(width: Int, char: Char = '='): String {
        return "${char.toString().repeat(width)}\n"
    }

    private fun formatColumns(left: String, center: String, right: String, width: Int): String {
        val rightWidth = 10
        val centerWidth = 6
        val leftWidth = width - rightWidth - centerWidth

        val l = left.padEnd(leftWidth).take(leftWidth)
        val c = center.padStart(centerWidth).take(centerWidth)
        val r = right.padStart(rightWidth).take(rightWidth)
        return "$l$c$r\n"
    }

    private fun formatRowRight(label: String, value: String, width: Int): String {
        val totalNeeded = label.length + value.length
        return if (totalNeeded <= width) {
            val spaces = " ".repeat(width - totalNeeded)
            "$spaces$label$value\n"
        } else {
            "$label$value\n"
        }
    }
}
