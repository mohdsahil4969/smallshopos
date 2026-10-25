package com.syntaxa.khata.feature.receipts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.syntaxa.khata.core.model.CurrencyFormatter
import com.syntaxa.khata.core.model.DateTimeUtils
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import com.syntaxa.khata.domain.model.BillWithItems
import com.syntaxa.khata.domain.model.CustomerStatement
import java.io.File
import java.io.FileOutputStream

object PdfReceiptGenerator {
    private const val PAGE_WIDTH = 595 // Standard A4 width in points
    private const val PAGE_HEIGHT = 842 // Standard A4 height in points

    fun generateBillReceipt(
        context: Context,
        shop: ShopProfileEntity,
        billWithItems: BillWithItems
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawBillReceipt(canvas, shop, billWithItems)

        document.finishPage(page)

        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val outputFile = File(receiptsDir, "${billWithItems.bill.receiptNumber}.pdf")
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return outputFile
    }

    fun generateStatementReceipt(
        context: Context,
        shop: ShopProfileEntity,
        statement: CustomerStatement
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawCustomerStatement(canvas, shop, statement)

        document.finishPage(page)

        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val filename = "STATEMENT_${statement.customer.id}_${DateTimeUtils.formatReceiptTimestamp(System.currentTimeMillis())}.pdf"
        val outputFile = File(receiptsDir, filename)
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return outputFile
    }

    fun generateTransactionReceipt(
        context: Context,
        shop: ShopProfileEntity,
        customer: CustomerEntity,
        transaction: FinancialTransactionEntity,
        currentBalancePaise: Long
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawSingleTransaction(canvas, shop, customer, transaction, currentBalancePaise)

        document.finishPage(page)

        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val prefix = if (transaction.type == TransactionConstants.TYPE_PAYMENT_RECEIVED) "PAY" else "UDHAAR"
        val filename = "${prefix}_TXN_${transaction.id}_${DateTimeUtils.formatReceiptTimestamp(System.currentTimeMillis())}.pdf"
        val outputFile = File(receiptsDir, filename)
        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return outputFile
    }

    private fun drawBillReceipt(
        canvas: Canvas,
        shop: ShopProfileEntity,
        billWithItems: BillWithItems
    ) {
        val bill = billWithItems.bill
        val items = billWithItems.items
        val customer = billWithItems.customer

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(27, 94, 32)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }
        val dashPaint = Paint().apply {
            color = Color.rgb(180, 180, 180)
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }

        var y = 50f
        val centerX = PAGE_WIDTH / 2f
        val leftX = 40f
        val rightX = PAGE_WIDTH - 40f

        // Shop Header
        val shopDisplayName = shop.shopName.ifBlank { "Syntaxa Khata" }
        canvas.drawText(shopDisplayName, centerX, y, headerPaint)
        y += 20f

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 11f
        if (shop.address.isNotBlank()) {
            canvas.drawText(shop.address, centerX, y, textPaint)
            y += 16f
        }
        if (shop.phone.isNotBlank()) {
            canvas.drawText("Phone: ${shop.phone}", centerX, y, textPaint)
            y += 16f
        }

        y += 10f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 24f

        // Receipt Meta
        textPaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("TAX / RETAIL INVOICE", leftX, y, boldPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${DateTimeUtils.formatDateTime(bill.createdAt)}", rightX, y, textPaint)
        y += 18f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Receipt No: ${bill.receiptNumber}", leftX, y, textPaint)
        y += 18f

        if (customer != null) {
            canvas.drawText("Customer: ${customer.name}", leftX, y, boldPaint)
            if (customer.phone.isNotBlank()) {
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Ph: ${customer.phone}", rightX, y, textPaint)
            }
            y += 18f
        } else {
            canvas.drawText("Customer: Walk-in Cash Customer", leftX, y, textPaint)
            y += 18f
        }

        y += 10f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 20f

        // Table Header
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Item / Description", leftX, y, boldPaint)
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Qty", 340f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Rate", 440f, y, boldPaint)
        canvas.drawText("Total", rightX, y, boldPaint)
        y += 10f
        canvas.drawLine(leftX, y, rightX, y, dashPaint)
        y += 20f

        textPaint.textAlign = Paint.Align.LEFT
        if (items.isNotEmpty()) {
            for (item in items) {
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(item.description, leftX, y, textPaint)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(String.format("%.1f", item.quantity), 340f, y, textPaint)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(CurrencyFormatter.formatPaise(item.unitPricePaise, showSymbol = false), 440f, y, textPaint)
                canvas.drawText(CurrencyFormatter.formatPaise(item.lineTotalPaise), rightX, y, textPaint)
                y += 20f
            }
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            val desc = if (bill.notes.isNotBlank()) bill.notes else "General Goods / Groceries"
            canvas.drawText(desc, leftX, y, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("1", 340f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(CurrencyFormatter.formatPaise(bill.subtotalPaise, showSymbol = false), 440f, y, textPaint)
            canvas.drawText(CurrencyFormatter.formatPaise(bill.subtotalPaise), rightX, y, textPaint)
            y += 20f
        }

        y += 10f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 20f

        // Totals
        textPaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.RIGHT
        if (bill.discountPaise > 0) {
            canvas.drawText("Subtotal:", 380f, y, textPaint)
            canvas.drawText(CurrencyFormatter.formatPaise(bill.subtotalPaise), rightX, y, boldPaint)
            y += 20f
            canvas.drawText("Discount:", 380f, y, textPaint)
            canvas.drawText("-${CurrencyFormatter.formatPaise(bill.discountPaise)}", rightX, y, boldPaint)
            y += 20f
        }

        boldPaint.textSize = 14f
        canvas.drawText("Total Bill Amount:", 380f, y, boldPaint)
        canvas.drawText(CurrencyFormatter.formatPaise(bill.totalPaise), rightX, y, boldPaint)
        y += 22f
        boldPaint.textSize = 12f

        val methodLabel = if (bill.paymentMethod == "SPLIT") {
            "Split: Cash ${CurrencyFormatter.formatPaise(bill.cashPaidPaise)} + UPI ${CurrencyFormatter.formatPaise(bill.upiPaidPaise)}"
        } else {
            bill.paymentMethod
        }
        canvas.drawText("Amount Received ($methodLabel):", 380f, y, textPaint)
        canvas.drawText(CurrencyFormatter.formatPaise(bill.amountPaidPaise), rightX, y, boldPaint)
        y += 20f

        val balanceOwed = bill.totalPaise - bill.amountPaidPaise
        if (balanceOwed > 0) {
            boldPaint.color = Color.rgb(198, 40, 40)
            canvas.drawText("Balance Owed (Udhaar):", 380f, y, boldPaint)
            canvas.drawText(CurrencyFormatter.formatPaise(balanceOwed), rightX, y, boldPaint)
            boldPaint.color = Color.BLACK
            y += 20f
        }

        y += 30f
        canvas.drawLine(leftX, y, rightX, y, dashPaint)
        y += 24f

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 12f
        textPaint.color = Color.rgb(100, 100, 100)
        if (shop.receiptFooter.isNotBlank()) {
            canvas.drawText(shop.receiptFooter, centerX, y, textPaint)
            y += 18f
        }
        textPaint.textSize = 9f
        canvas.drawText("Generated offline with Syntaxa Khata", centerX, y, textPaint)
    }

    private fun drawCustomerStatement(
        canvas: Canvas,
        shop: ShopProfileEntity,
        statement: CustomerStatement
    ) {
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 11f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(27, 94, 32)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }
        val dashPaint = Paint().apply {
            color = Color.rgb(180, 180, 180)
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }

        var y = 50f
        val centerX = PAGE_WIDTH / 2f
        val leftX = 40f
        val rightX = PAGE_WIDTH - 40f

        val shopDisplayName = shop.shopName.ifBlank { "Syntaxa Khata" }
        canvas.drawText(shopDisplayName, centerX, y, headerPaint)
        y += 20f
        textPaint.textAlign = Paint.Align.CENTER
        if (shop.phone.isNotBlank()) {
            canvas.drawText("Phone: ${shop.phone}", centerX, y, textPaint)
            y += 16f
        }
        y += 10f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 24f

        boldPaint.textSize = 14f
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("CUSTOMER ACCOUNT STATEMENT", leftX, y, boldPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        val periodLabel = if (statement.startDate != null && statement.endDate != null) {
            "${DateTimeUtils.formatDate(statement.startDate)} to ${DateTimeUtils.formatDate(statement.endDate)}"
        } else {
            "As of: ${DateTimeUtils.formatDate(System.currentTimeMillis())}"
        }
        canvas.drawText(periodLabel, rightX, y, textPaint)
        y += 22f

        boldPaint.textSize = 12f
        canvas.drawText("Customer: ${statement.customer.name}", leftX, y, boldPaint)
        if (statement.customer.phone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Mobile: ${statement.customer.phone}", rightX, y, textPaint)
        }
        y += 22f

        val boxTop = y
        val boxBottom = y + 50f
        val boxPaint = Paint().apply {
            color = Color.rgb(240, 248, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(leftX, boxTop, rightX, boxBottom, boxPaint)
        canvas.drawRect(leftX, boxTop, rightX, boxBottom, linePaint)

        boldPaint.textSize = 14f
        boldPaint.textAlign = Paint.Align.LEFT
        val balanceLabel = if (statement.startDate != null) "Closing Balance for Period:" else "Current Balance Due:"
        canvas.drawText(balanceLabel, leftX + 16f, y + 30f, boldPaint)

        boldPaint.textAlign = Paint.Align.RIGHT
        val displayBal = if (statement.startDate != null) statement.closingBalancePaise else statement.currentBalancePaise
        boldPaint.color = if (displayBal > 0) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
        canvas.drawText(CurrencyFormatter.formatPaise(displayBal), rightX - 16f, y + 30f, boldPaint)
        boldPaint.color = Color.BLACK
        boldPaint.textSize = 11f

        y = boxBottom + 25f

        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date & Time", leftX, y, boldPaint)
        canvas.drawText("Particulars / Type", 170f, y, boldPaint)
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Udhaar (+)", 430f, y, boldPaint)
        canvas.drawText("Payment (-)", rightX, y, boldPaint)
        y += 10f
        canvas.drawLine(leftX, y, rightX, y, dashPaint)
        y += 18f

        // Draw opening balance row if non-zero
        if (statement.openingBalancePaise != 0L) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("-", leftX, y, textPaint)
            canvas.drawText("Opening Balance", 170f, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            if (statement.openingBalancePaise > 0) {
                canvas.drawText(CurrencyFormatter.formatPaise(statement.openingBalancePaise), 430f, y, textPaint)
            } else {
                canvas.drawText(CurrencyFormatter.formatPaise(-statement.openingBalancePaise), rightX, y, textPaint)
            }
            y += 18f
        }

        textPaint.textAlign = Paint.Align.LEFT
        for (tx in statement.transactions.take(25)) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(DateTimeUtils.formatDate(tx.occurredAt), leftX, y, textPaint)

            val desc = when {
                tx.type == "UDHAAR_GIVEN" -> "Udhaar Given"
                tx.type == "PAYMENT_RECEIVED" -> "Payment (${tx.paymentMethod})"
                else -> "Reversal"
            }
            val fullDesc = if (tx.description.isNotBlank()) "$desc - ${tx.description}" else desc
            canvas.drawText(fullDesc.take(30), 170f, y, textPaint)

            textPaint.textAlign = Paint.Align.RIGHT
            if (tx.type == "UDHAAR_GIVEN") {
                canvas.drawText(CurrencyFormatter.formatPaise(tx.amountPaise), 430f, y, textPaint)
            } else if (tx.type == "PAYMENT_RECEIVED") {
                canvas.drawText(CurrencyFormatter.formatPaise(tx.amountPaise), rightX, y, textPaint)
            }
            y += 18f
        }

        y += 15f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 20f

        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total Udhaar in Period: ${CurrencyFormatter.formatPaise(statement.totalUdhaarPaise)}", 430f, y, boldPaint)
        canvas.drawText("Total Paid in Period: ${CurrencyFormatter.formatPaise(statement.totalPaidPaise)}", rightX, y, boldPaint)
        y += 20f

        if (statement.startDate != null) {
            canvas.drawText("Net Closing Balance: ${CurrencyFormatter.formatPaise(statement.closingBalancePaise)}", rightX, y, boldPaint)
            y += 20f
        }
        y += 10f

        if (shop.receiptFooter.isNotBlank()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.rgb(100, 100, 100)
            canvas.drawText(shop.receiptFooter, centerX, y, textPaint)
        }
    }

    private fun drawSingleTransaction(
        canvas: Canvas,
        shop: ShopProfileEntity,
        customer: CustomerEntity,
        transaction: FinancialTransactionEntity,
        currentBalancePaise: Long
    ) {
        val isPayment = transaction.type == TransactionConstants.TYPE_PAYMENT_RECEIVED
        val title = if (isPayment) "PAYMENT RECEIPT" else "UDHAAR TRANSACTION RECEIPT"

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
        }
        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = if (isPayment) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }

        var y = 50f
        val centerX = PAGE_WIDTH / 2f
        val leftX = 40f
        val rightX = PAGE_WIDTH - 40f

        // Shop Header
        val shopDisplayName = shop.shopName.ifBlank { "Syntaxa Khata" }
        canvas.drawText(shopDisplayName, centerX, y, headerPaint)
        y += 20f
        textPaint.textAlign = Paint.Align.CENTER
        if (shop.phone.isNotBlank()) {
            canvas.drawText("Phone: ${shop.phone}", centerX, y, textPaint)
            y += 16f
        }
        y += 10f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 24f

        // Title & Meta
        boldPaint.textSize = 14f
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(title, leftX, y, boldPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Date: ${DateTimeUtils.formatDateTime(transaction.occurredAt)}", rightX, y, textPaint)
        y += 20f

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Receipt Ref: #TXN-${transaction.id}", leftX, y, textPaint)
        y += 20f

        boldPaint.textSize = 12f
        canvas.drawText("Customer: ${customer.name}", leftX, y, boldPaint)
        if (customer.phone.isNotBlank()) {
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Ph: ${customer.phone}", rightX, y, textPaint)
        }
        y += 24f

        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 24f

        // Transaction Summary Card Box
        val boxTop = y
        val boxBottom = y + 70f
        val boxPaint = Paint().apply {
            color = if (isPayment) Color.rgb(232, 245, 233) else Color.rgb(255, 235, 238)
            style = Paint.Style.FILL
        }
        canvas.drawRect(leftX, boxTop, rightX, boxBottom, boxPaint)
        canvas.drawRect(leftX, boxTop, rightX, boxBottom, linePaint)

        boldPaint.textSize = 13f
        canvas.drawText(if (isPayment) "Amount Paid (${transaction.paymentMethod}):" else "Udhaar Extended:", leftX + 16f, y + 28f, boldPaint)

        boldPaint.textSize = 18f
        boldPaint.textAlign = Paint.Align.RIGHT
        boldPaint.color = if (isPayment) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40)
        canvas.drawText(CurrencyFormatter.formatPaise(transaction.amountPaise), rightX - 16f, y + 30f, boldPaint)
        boldPaint.color = Color.BLACK
        boldPaint.textSize = 12f

        if (transaction.description.isNotBlank()) {
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("Note: ${transaction.description}", leftX + 16f, y + 52f, textPaint)
        }

        y = boxBottom + 30f

        // Current Balance Status
        boldPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Current Total Balance Owed:", leftX, y, boldPaint)
        boldPaint.textAlign = Paint.Align.RIGHT
        boldPaint.color = if (currentBalancePaise > 0) Color.rgb(198, 40, 40) else Color.rgb(46, 125, 50)
        canvas.drawText(CurrencyFormatter.formatPaise(currentBalancePaise), rightX, y, boldPaint)
        boldPaint.color = Color.BLACK
        y += 20f

        y += 20f
        canvas.drawLine(leftX, y, rightX, y, linePaint)
        y += 25f

        if (shop.receiptFooter.isNotBlank()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.rgb(100, 100, 100)
            canvas.drawText(shop.receiptFooter, centerX, y, textPaint)
        }
    }
}
