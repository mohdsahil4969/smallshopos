package com.syntaxa.khata.feature.receipts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.syntaxa.khata.R
import com.syntaxa.khata.core.model.CurrencyFormatter
import java.io.File

object ShareHelper {

    private fun safeStartActivity(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun getWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val knownPackages = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.whatsapp.clone",
            "com.gbwhatsapp",
            "com.fmwhatsapp",
            "com.yowhatsapp"
        )
        for (pkg in knownPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
            }
        }
        for (mime in listOf("application/pdf", "*/*")) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    if (pkg.contains("whatsapp", ignoreCase = true)) {
                        return pkg
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Cleans and formats phone numbers for WhatsApp deep links.
     * Automatically prepends India country code '91' for 10-digit mobile numbers.
     */
    fun formatPhoneNumberForWhatsApp(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return when {
            digitsOnly.length == 10 -> "91$digitsOnly"
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> "91${digitsOnly.substring(1)}"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly
            digitsOnly.isNotBlank() -> digitsOnly
            else -> ""
        }
    }

    fun sharePdf(context: Context, pdfFile: File, title: String = "Share Receipt") {
        shareFile(context, pdfFile, mimeType = "application/pdf", title = title)
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*", title: String = "Share File") {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_with_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareViaWhatsApp(context: Context, pdfFile: File, phone: String = "", caption: String = "") {
        val cleanPhone = formatPhoneNumberForWhatsApp(phone)
        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_with_message, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            return
        }

        val detectedPkg = getWhatsAppPackage(context)
        val candidatePackages = listOfNotNull(
            detectedPkg,
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.whatsapp.clone",
            "com.gbwhatsapp",
            "com.fmwhatsapp",
            "com.yowhatsapp"
        ).distinct()

        var launched = false
        val mimeTypes = listOf("application/pdf", "*/*")

        for (pkg in candidatePackages) {
            val packageExists = try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
            if (!packageExists && pkg != detectedPkg) continue

            if (packageExists) {
                try {
                    context.grantUriPermission(pkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }

            for (mime in mimeTypes) {
                try {
                    val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        setPackage(pkg)
                        clipData = ClipData.newRawUri("receipt", contentUri)
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        if (caption.isNotBlank()) {
                            putExtra(Intent.EXTRA_TEXT, caption)
                        }
                        if (cleanPhone.isNotBlank()) {
                            val jid = "$cleanPhone@s.whatsapp.net"
                            putExtra("jid", jid)
                            putExtra("user_jid", jid)
                            putExtra("chat_jid", jid)
                            putExtra(Intent.EXTRA_PHONE_NUMBER, cleanPhone)
                            putExtra("phone", cleanPhone)
                            putExtra("address", cleanPhone)
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(whatsappIntent)
                    launched = true
                    break
                } catch (_: Exception) {
                }
            }
            if (launched) break
        }

        if (!launched) {
            if (cleanPhone.isNotBlank() && caption.isNotBlank()) {
                shareTextViaWhatsApp(context, caption, phone)
                Toast.makeText(context, "Opened WhatsApp chat. To attach PDF, select it from your files.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, context.getString(R.string.whatsapp_not_installed), Toast.LENGTH_SHORT).show()
                sharePdf(context, pdfFile)
            }
        }
    }

    fun shareText(context: Context, text: String, title: String = "Send Payment Reminder") {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            copyToClipboard(context, text)
        }
    }

    /**
     * Directly launches WhatsApp into the customer's chat screen with prefilled text.
     * Eliminates the need to search or select a contact.
     */
    fun shareTextViaWhatsApp(context: Context, text: String, phone: String = "") {
        val cleanPhone = formatPhoneNumberForWhatsApp(phone)
        try {
            val whatsappPackage = getWhatsAppPackage(context)
            val intent = if (cleanPhone.isNotBlank()) {
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}"
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (whatsappPackage != null) setPackage(whatsappPackage)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    if (whatsappPackage != null) setPackage(whatsappPackage)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                if (cleanPhone.isNotBlank()) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } else {
                    shareText(context, text)
                }
            } catch (_: Exception) {
                Toast.makeText(context, "WhatsApp not available. Opening share menu...", Toast.LENGTH_SHORT).show()
                shareText(context, text)
            }
        }
    }

    fun buildBillWhatsAppText(
        shopName: String,
        receiptNumber: String,
        customerName: String?,
        items: List<Pair<String, Long>>,
        subtotalPaise: Long,
        discountPaise: Long,
        totalPaise: Long,
        amountPaidPaise: Long,
        paymentMethod: String,
        notes: String = ""
    ): String {
        val sb = StringBuilder()
        sb.append("🧾 *${shopName.ifBlank { "SmartKhata" }}*\n")
        sb.append("Invoice: *$receiptNumber*\n")
        if (!customerName.isNullOrBlank()) {
            sb.append("Customer: $customerName\n")
        }
        sb.append("-----------------------------\n")
        if (items.isNotEmpty()) {
            for ((desc, lineTotal) in items) {
                sb.append("• $desc: ${CurrencyFormatter.formatPaise(lineTotal)}\n")
            }
        } else if (notes.isNotBlank()) {
            sb.append("• $notes: ${CurrencyFormatter.formatPaise(subtotalPaise)}\n")
        }
        sb.append("-----------------------------\n")
        if (discountPaise > 0L) {
            sb.append("Subtotal: ${CurrencyFormatter.formatPaise(subtotalPaise)}\n")
            sb.append("Discount: -${CurrencyFormatter.formatPaise(discountPaise)}\n")
        }
        sb.append("*Total Amount:* ${CurrencyFormatter.formatPaise(totalPaise)}\n")
        sb.append("Paid ($paymentMethod): ${CurrencyFormatter.formatPaise(amountPaidPaise)}\n")
        val balance = totalPaise - amountPaidPaise
        if (balance > 0L) {
            sb.append("*Balance Due (Udhaar):* ${CurrencyFormatter.formatPaise(balance)}\n")
        }
        sb.append("\nThank you for shopping with us! 🙏")
        return sb.toString()
    }

    fun shareTextViaSms(context: Context, text: String, phone: String = "") {
        try {
            val cleanPhone = formatPhoneNumberForWhatsApp(phone)
            val uri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:$cleanPhone") else Uri.parse("smsto:")
            val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
        } catch (e: Exception) {
            shareText(context, text, "Send SMS")
        }
    }

    fun shareViaEmail(
        context: Context,
        subject: String,
        body: String,
        pdfFile: File? = null,
        emailAddress: String = ""
    ) {
        try {
            val intent = if (pdfFile != null) {
                val contentUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    if (emailAddress.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$emailAddress")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            val chooser = Intent.createChooser(intent, "Send Email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch email: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "SmartKhata") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
