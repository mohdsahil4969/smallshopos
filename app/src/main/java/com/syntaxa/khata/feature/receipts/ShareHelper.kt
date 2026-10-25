package com.syntaxa.khata.feature.receipts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    private fun safeStartActivity(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun getWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun sharePdf(context: Context, pdfFile: File, title: String = "Share Receipt") {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
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
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareViaWhatsApp(context: Context, pdfFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val whatsappPackage = getWhatsAppPackage(context)
            if (whatsappPackage != null) {
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    setPackage(whatsappPackage)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(whatsappIntent)
            } else {
                Toast.makeText(context, "WhatsApp not found. Opening other apps...", Toast.LENGTH_SHORT).show()
                sharePdf(context, pdfFile)
            }
        } catch (e: Exception) {
            // WhatsApp is not installed or failed -> Fallback to general share sheet
            Toast.makeText(context, "WhatsApp not available. Opening other apps...", Toast.LENGTH_SHORT).show()
            sharePdf(context, pdfFile)
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

    fun shareTextViaWhatsApp(context: Context, text: String, phone: String = "") {
        try {
            val whatsappPackage = getWhatsAppPackage(context)
            val intent = if (phone.isNotBlank()) {
                val cleanPhone = phone.replace("+", "").replace(" ", "").trim()
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
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not available. Opening share menu...", Toast.LENGTH_SHORT).show()
            shareText(context, text)
        }
    }

    fun shareTextViaSms(context: Context, text: String, phone: String = "") {
        try {
            val cleanPhone = phone.replace("+", "").replace(" ", "").trim()
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

    fun copyToClipboard(context: Context, text: String, label: String = "Syntaxa Khata") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
