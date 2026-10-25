package com.syntaxa.khata.feature.backup

import android.content.Context
import androidx.room.withTransaction
import com.syntaxa.khata.data.local.KhataDatabase
import com.syntaxa.khata.data.local.KhataDatabaseSource
import com.syntaxa.khata.data.local.entity.BillConstants
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import com.syntaxa.khata.data.local.entity.ExpenseConstants
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import com.syntaxa.khata.data.local.entity.TransactionConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupValidationResult(
    val isValid: Boolean,
    val isEncrypted: Boolean,
    val version: Int,
    val customerCount: Int,
    val transactionCount: Int,
    val billCount: Int,
    val expenseCount: Int,
    val productCount: Int = 0,
    val supplierCount: Int = 0,
    val supplierTransactionCount: Int = 0,
    val dailyClosingCount: Int = 0,
    val errorMessage: String? = null
)

data class ParsedBackupData(
    val profile: ShopProfileEntity?,
    val customers: List<CustomerEntity>,
    val transactions: List<FinancialTransactionEntity>,
    val bills: List<BillEntity>,
    val billItems: List<BillItemEntity>,
    val expenses: List<ExpenseEntity>,
    val products: List<ProductEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val supplierTransactions: List<SupplierTransactionEntity> = emptyList(),
    val dailyCashClosings: List<DailyCashClosingEntity> = emptyList()
)

class BackupManager(
    private val database: KhataDatabaseSource,
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        if (database is KhataDatabase) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }
) {
    constructor(context: Context, database: KhataDatabase) : this(database as KhataDatabaseSource)
    constructor(database: KhataDatabase) : this(database as KhataDatabaseSource)

    companion object {
        const val FORMAT_IDENTIFIER = "SYNTAXA_KHATA_BACKUP"
        const val SAFETY_FORMAT_IDENTIFIER = "SYNTAXA_KHATA_SAFETY_BACKUP"
        const val BACKUP_VERSION_1_PLAINTEXT = 1
        const val BACKUP_VERSION_2_ENCRYPTED = 2
        const val SAFETY_BACKUP_VERSION = 1

        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

        const val MIN_PBKDF2_ITERATIONS = 50_000
        const val MAX_PBKDF2_ITERATIONS = 500_000
        const val DEFAULT_PBKDF2_ITERATIONS = 100_000

        const val KEY_LENGTH_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val SALT_LENGTH_BYTES = 16

        const val MIN_PASSWORD_LENGTH = 12
        const val MAX_BACKUP_FILE_BYTES = 25 * 1024 * 1024L // 25 MB max limit
    }

    /**
     * Exports database into an AES-256-GCM authenticated encrypted backup using user-supplied password.
     * Derives a 256-bit key via PBKDF2WithHmacSHA256, generates fresh CSPRNG salt & nonce, and authenticates AAD.
     */
    suspend fun exportEncryptedBackup(
        outputStream: OutputStream,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Backup encryption password must be at least $MIN_PASSWORD_LENGTH characters"
        }

        val plainJsonString = buildPayloadJson().toString()
        val payloadBytes = plainJsonString.toByteArray(StandardCharsets.UTF_8)

        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val passwordChars = password.toCharArray()
        val keyBytes: ByteArray
        try {
            val keySpec = PBEKeySpec(passwordChars, salt, DEFAULT_PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val secretKeyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
        } finally {
            passwordChars.fill('\u0000')
        }

        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val createdAt = System.currentTimeMillis()
        val aad = "$FORMAT_IDENTIFIER:$BACKUP_VERSION_2_ENCRYPTED:$createdAt".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)

        val cipherBytes = cipher.doFinal(payloadBytes)

        val root = JSONObject().apply {
            put("format", FORMAT_IDENTIFIER)
            put("version", BACKUP_VERSION_2_ENCRYPTED)
            put("encrypted", true)
            put("cipher", CIPHER_ALGORITHM)
            put("kdf", PBKDF2_ALGORITHM)
            put("iterations", DEFAULT_PBKDF2_ITERATIONS)
            put("saltHex", bytesToHex(salt))
            put("ivHex", bytesToHex(iv))
            put("createdAt", createdAt)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(cipherBytes))
        }

        outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
            writer.flush()
        }
        true
    }

    /**
     * Exports an automated authenticated safety backup prior to an in-app update.
     * Uses AES-256-GCM with a 256-bit internal safety key and fresh IV.
     */
    suspend fun exportSafetyBackup(
        outputStream: OutputStream,
        safetyKey: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        require(safetyKey.size == 32) { "Safety key must be 256 bits (32 bytes)" }

        val plainJsonString = buildPayloadJson().toString()
        val payloadBytes = plainJsonString.toByteArray(StandardCharsets.UTF_8)

        val secureRandom = SecureRandom()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(safetyKey, "AES")
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val createdAt = System.currentTimeMillis()
        val aad = "$SAFETY_FORMAT_IDENTIFIER:$SAFETY_BACKUP_VERSION:$createdAt".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)

        val cipherBytes = cipher.doFinal(payloadBytes)

        val root = JSONObject().apply {
            put("format", SAFETY_FORMAT_IDENTIFIER)
            put("version", SAFETY_BACKUP_VERSION)
            put("encrypted", true)
            put("cipher", CIPHER_ALGORITHM)
            put("ivHex", bytesToHex(iv))
            put("createdAt", createdAt)
            put("ciphertextBase64", Base64.getEncoder().encodeToString(cipherBytes))
        }

        outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
            writer.flush()
        }
        true
    }

    /**
     * Decrypts and tests recoverability of a safety backup before allowing an update to proceed.
     */
    suspend fun verifySafetyBackup(
        inputStream: InputStream,
        safetyKey: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            require(safetyKey.size == 32) { "Safety key must be 256 bits (32 bytes)" }
            val jsonString = readBoundedString(inputStream, MAX_BACKUP_FILE_BYTES)
            val root = JSONObject(jsonString)

            if (root.optString("format") != SAFETY_FORMAT_IDENTIFIER) return@withContext false
            if (root.optInt("version") != SAFETY_BACKUP_VERSION) return@withContext false
            if (!root.optBoolean("encrypted", false)) return@withContext false

            val iv = hexToBytes(root.getString("ivHex"))
            val cipherBytes = Base64.getDecoder().decode(root.getString("ciphertextBase64"))
            val createdAt = root.getLong("createdAt")

            val secretKey = SecretKeySpec(safetyKey, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val aad = "$SAFETY_FORMAT_IDENTIFIER:$SAFETY_BACKUP_VERSION:$createdAt".toByteArray(StandardCharsets.UTF_8)
            cipher.updateAAD(aad)

            val plainBytes = cipher.doFinal(cipherBytes)
            val plainText = String(plainBytes, StandardCharsets.UTF_8)
            val payloadObj = JSONObject(plainText)

            val validation = validatePayloadJson(payloadObj, isEncrypted = true, version = SAFETY_BACKUP_VERSION)
            validation.isValid
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports database into a standard unencrypted JSON backup with explicit non-encrypted metadata
     * and a verified 64-character SHA-256 checksum over the payload.
     */
    suspend fun exportUnencryptedBackup(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val root = buildPayloadJson()
        root.put("format", FORMAT_IDENTIFIER)
        root.put("version", BACKUP_VERSION_1_PLAINTEXT)
        root.put("encrypted", false)

        val canonicalString = canonicalJsonString(root)
        val checksum = calculateSha256(canonicalString)
        root.put("checksum", checksum)

        outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
            writer.flush()
        }
        true
    }

    private suspend fun buildPayloadJson(): JSONObject {
        val root = JSONObject()
        root.put("createdAt", System.currentTimeMillis())

        // Shop Profile
        val profile = database.shopProfileDao().getShopProfile()
        if (profile != null) {
            val pObj = JSONObject().apply {
                put("shopName", profile.shopName)
                put("address", profile.address)
                put("phone", profile.phone)
                put("upiId", profile.upiId)
                put("receiptFooter", profile.receiptFooter)
                put("language", profile.language)
            }
            root.put("shopProfile", pObj)
        }

        // Customers
        val customers = database.customerDao().getAllCustomersRaw()
        val cArray = JSONArray()
        for (c in customers) {
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("notes", c.notes)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
                if (c.archivedAt != null) put("archivedAt", c.archivedAt)
                if (c.dueDate != null) put("dueDate", c.dueDate)
            }
            cArray.put(cObj)
        }
        root.put("customers", cArray)

        // Transactions
        val transactions = database.financialTransactionDao().getAllTransactionsRaw()
        val tArray = JSONArray()
        for (t in transactions) {
            val tObj = JSONObject().apply {
                put("id", t.id)
                put("type", t.type)
                if (t.customerId != null) put("customerId", t.customerId)
                put("amountPaise", t.amountPaise)
                put("paymentMethod", t.paymentMethod)
                put("description", t.description)
                put("occurredAt", t.occurredAt)
                put("createdAt", t.createdAt)
                if (t.reversalOfTransactionId != null) put("reversalOfTransactionId", t.reversalOfTransactionId)
                if (t.relatedBillId != null) put("relatedBillId", t.relatedBillId)
                put("idempotencyKey", t.idempotencyKey)
                put("status", t.status)
                if (t.dueDate != null) put("dueDate", t.dueDate)
            }
            tArray.put(tObj)
        }
        root.put("transactions", tArray)

        // Bills
        val bills = database.billDao().getAllBillsRaw()
        val bArray = JSONArray()
        for (b in bills) {
            val bObj = JSONObject().apply {
                put("id", b.id)
                put("receiptNumber", b.receiptNumber)
                if (b.customerId != null) put("customerId", b.customerId)
                put("subtotalPaise", b.subtotalPaise)
                put("discountPaise", b.discountPaise)
                put("totalPaise", b.totalPaise)
                put("amountPaidPaise", b.amountPaidPaise)
                put("paymentMethod", b.paymentMethod)
                put("billType", b.billType)
                put("notes", b.notes)
                put("createdAt", b.createdAt)
                put("status", b.status)
                put("cashPaidPaise", b.cashPaidPaise)
                put("upiPaidPaise", b.upiPaidPaise)
                put("otherPaidPaise", b.otherPaidPaise)
                put("idempotencyKey", b.idempotencyKey)
            }
            bArray.put(bObj)
        }
        root.put("bills", bArray)

        // Bill Items
        val items = database.billDao().getAllBillItemsRaw()
        val biArray = JSONArray()
        for (i in items) {
            val iObj = JSONObject().apply {
                put("id", i.id)
                put("billId", i.billId)
                put("description", i.description)
                put("quantity", i.quantity)
                put("unitPricePaise", i.unitPricePaise)
                put("lineTotalPaise", i.lineTotalPaise)
                if (i.productId != null) put("productId", i.productId)
            }
            biArray.put(iObj)
        }
        root.put("billItems", biArray)

        // Expenses
        val expenses = database.expenseDao().getAllExpensesRaw()
        val eArray = JSONArray()
        for (e in expenses) {
            val eObj = JSONObject().apply {
                put("id", e.id)
                put("amountPaise", e.amountPaise)
                put("category", e.category)
                put("description", e.description)
                put("paymentMethod", e.paymentMethod)
                put("occurredAt", e.occurredAt)
                put("createdAt", e.createdAt)
                put("status", e.status)
            }
            eArray.put(eObj)
        }
        root.put("expenses", eArray)

        // Products
        val products = database.productDao().getAllProductsRaw()
        val pArray = JSONArray()
        for (p in products) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("unit", p.unit)
                put("sellingPricePaise", p.sellingPricePaise)
                put("costPricePaise", p.costPricePaise)
                put("stockQuantity", p.stockQuantity)
                put("lowStockThreshold", p.lowStockThreshold)
                put("barcode", p.barcode)
                put("category", p.category)
                put("createdAt", p.createdAt)
                put("updatedAt", p.updatedAt)
                put("isArchived", p.isArchived)
            }
            pArray.put(pObj)
        }
        root.put("products", pArray)

        // Suppliers
        val suppliers = database.supplierDao().getAllSuppliersRaw()
        val sArray = JSONArray()
        for (s in suppliers) {
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("phone", s.phone)
                put("notes", s.notes)
                put("createdAt", s.createdAt)
                put("updatedAt", s.updatedAt)
                if (s.archivedAt != null) put("archivedAt", s.archivedAt)
            }
            sArray.put(sObj)
        }
        root.put("suppliers", sArray)

        // Supplier Transactions
        val sTxs = database.supplierDao().getAllTransactionsRaw()
        val stArray = JSONArray()
        for (st in sTxs) {
            val stObj = JSONObject().apply {
                put("id", st.id)
                put("supplierId", st.supplierId)
                put("type", st.type)
                put("amountPaise", st.amountPaise)
                put("paymentMethod", st.paymentMethod)
                put("billInvoiceNumber", st.billInvoiceNumber)
                put("notes", st.notes)
                put("occurredAt", st.occurredAt)
                put("createdAt", st.createdAt)
                if (st.reversalOfTransactionId != null) put("reversalOfTransactionId", st.reversalOfTransactionId)
                put("status", st.status)
                put("idempotencyKey", st.idempotencyKey)
            }
            stArray.put(stObj)
        }
        root.put("supplierTransactions", stArray)

        // Daily Cash Closings
        val closings = database.dailyCashClosingDao().getAllClosingsRaw()
        val dcArray = JSONArray()
        for (dc in closings) {
            val dcObj = JSONObject().apply {
                put("id", dc.id)
                put("closingDateEpoch", dc.closingDateEpoch)
                put("openingCashPaise", dc.openingCashPaise)
                put("cashSalesPaise", dc.cashSalesPaise)
                put("cashKhataCollectedPaise", dc.cashKhataCollectedPaise)
                put("cashExpensesPaise", dc.cashExpensesPaise)
                put("expectedCashPaise", dc.expectedCashPaise)
                put("countedCashPaise", dc.countedCashPaise)
                put("differencePaise", dc.differencePaise)
                put("notes", dc.notes)
                put("closedAt", dc.closedAt)
            }
            dcArray.put(dcObj)
        }
        root.put("dailyCashClosings", dcArray)

        return root
    }

    /**
     * Inspects backup headers without decrypting to validate structure and determine if a password is required.
     */
    suspend fun inspectBackup(inputStream: InputStream): BackupValidationResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = readBoundedString(inputStream, MAX_BACKUP_FILE_BYTES)
            val root = JSONObject(jsonString)

            val format = root.optString("format", "")
            if (format != FORMAT_IDENTIFIER) {
                return@withContext fail("Invalid backup format identifier: expected $FORMAT_IDENTIFIER")
            }

            val version = root.optInt("version", -1)
            if (version != BACKUP_VERSION_1_PLAINTEXT && version != BACKUP_VERSION_2_ENCRYPTED) {
                return@withContext fail("Unsupported backup version: $version")
            }

            val isEncrypted = root.optBoolean("encrypted", false)
            if (isEncrypted) {
                val metaValidation = validateEncryptedMetadata(root)
                if (metaValidation != null) {
                    return@withContext fail(metaValidation)
                }
                return@withContext BackupValidationResult(
                    isValid = true,
                    isEncrypted = true,
                    version = version,
                    customerCount = 0,
                    transactionCount = 0,
                    billCount = 0,
                    expenseCount = 0
                )
            } else {
                val checksumError = verifyPlaintextChecksum(root)
                if (checksumError != null) {
                    return@withContext fail(checksumError)
                }
                return@withContext validatePayloadJson(root, isEncrypted = false, version = version)
            }
        } catch (e: Exception) {
            fail("Corrupt backup file: ${e.localizedMessage}")
        }
    }

    /**
     * Decrypts, verifies cryptographic authentication, and thoroughly validates candidate backup dataset
     * before touching the database or initiating any restore transaction.
     */
    suspend fun validateAndParseBackup(
        inputStream: InputStream,
        password: String? = null
    ): Pair<BackupValidationResult, ParsedBackupData?> = withContext(Dispatchers.IO) {
        try {
            val jsonString = readBoundedString(inputStream, MAX_BACKUP_FILE_BYTES)
            val root = JSONObject(jsonString)

            val format = root.optString("format", "")
            if (format != FORMAT_IDENTIFIER) {
                return@withContext Pair(fail("Invalid backup format identifier: expected $FORMAT_IDENTIFIER"), null)
            }

            val version = root.optInt("version", -1)
            if (version != BACKUP_VERSION_1_PLAINTEXT && version != BACKUP_VERSION_2_ENCRYPTED) {
                return@withContext Pair(fail("Unsupported backup version: $version"), null)
            }

            val isEncrypted = root.optBoolean("encrypted", false)
            val payloadObj: JSONObject

            if (isEncrypted) {
                val metaError = validateEncryptedMetadata(root)
                if (metaError != null) {
                    return@withContext Pair(fail(metaError), null)
                }

                if (password.isNullOrEmpty() || password.length < MIN_PASSWORD_LENGTH) {
                    return@withContext Pair(
                        fail("Password must be at least $MIN_PASSWORD_LENGTH characters"),
                        null
                    )
                }

                payloadObj = decryptPayload(root, password)
            } else {
                val checksumError = verifyPlaintextChecksum(root)
                if (checksumError != null) {
                    return@withContext Pair(fail(checksumError), null)
                }
                payloadObj = root
            }

            val validation = validatePayloadJson(payloadObj, isEncrypted, version)
            if (!validation.isValid) {
                return@withContext Pair(validation, null)
            }

            val parsed = parsePayload(payloadObj)
            Pair(validation, parsed)
        } catch (e: javax.crypto.AEADBadTagException) {
            Pair(
                fail("Incorrect password or tampered backup file"),
                null
            )
        } catch (e: Exception) {
            Pair(
                fail("Backup validation failed: ${e.localizedMessage}"),
                null
            )
        }
    }

    private fun validateEncryptedMetadata(root: JSONObject): String? {
        if (!root.has("ciphertextBase64")) return "Encrypted backup missing ciphertext"
        if (!root.has("saltHex")) return "Encrypted backup missing salt"
        if (!root.has("ivHex")) return "Encrypted backup missing IV"
        if (!root.has("createdAt")) return "Encrypted backup missing creation timestamp"

        val cipherAlgo = root.optString("cipher", CIPHER_ALGORITHM)
        if (cipherAlgo != CIPHER_ALGORITHM) {
            return "Unsupported cipher algorithm: $cipherAlgo"
        }

        val kdfAlgo = root.optString("kdf", PBKDF2_ALGORITHM)
        if (kdfAlgo != PBKDF2_ALGORITHM) {
            return "Unsupported key derivation function: $kdfAlgo"
        }

        if (!root.has("iterations")) {
            return "Encrypted backup missing PBKDF2 iteration count"
        }
        val iterations = root.getInt("iterations")
        if (iterations < MIN_PBKDF2_ITERATIONS) {
            return "PBKDF2 iterations ($iterations) below security minimum ($MIN_PBKDF2_ITERATIONS)"
        }
        if (iterations > MAX_PBKDF2_ITERATIONS) {
            return "PBKDF2 iterations ($iterations) exceed safety maximum ($MAX_PBKDF2_ITERATIONS)"
        }

        val saltHex = root.getString("saltHex")
        if (saltHex.length != SALT_LENGTH_BYTES * 2 || !isValidHex(saltHex)) {
            return "Invalid salt length or format: expected 32 hex characters"
        }

        val ivHex = root.getString("ivHex")
        if (ivHex.length != GCM_IV_LENGTH_BYTES * 2 || !isValidHex(ivHex)) {
            return "Invalid IV length or format: expected 24 hex characters"
        }

        val ciphertextBase64 = root.getString("ciphertextBase64")
        if (ciphertextBase64.isBlank()) {
            return "Empty ciphertext in encrypted backup"
        }
        try {
            val decoded = Base64.getDecoder().decode(ciphertextBase64)
            if (decoded.size < 16) {
                return "Ciphertext payload is truncated (minimum 16 bytes for auth tag)"
            }
        } catch (_: Exception) {
            return "Ciphertext is not valid Base64"
        }

        return null
    }

    private fun verifyPlaintextChecksum(root: JSONObject): String? {
        if (!root.has("checksum")) {
            return "Unencrypted backup missing SHA-256 checksum"
        }
        val declaredChecksum = root.getString("checksum")
        if (declaredChecksum.length != 64 || !isValidHex(declaredChecksum)) {
            return "Unencrypted backup missing valid 64-character SHA-256 checksum"
        }

        val copy = JSONObject(root.toString())
        copy.remove("checksum")
        val canonicalHash = calculateSha256(canonicalJsonString(copy))
        val plainHash = calculateSha256(copy.toString())

        if (!canonicalHash.equals(declaredChecksum, ignoreCase = true) &&
            !plainHash.equals(declaredChecksum, ignoreCase = true)
        ) {
            return "Backup checksum mismatch: data has been altered or corrupted"
        }

        return null
    }

    private fun decryptPayload(root: JSONObject, password: String): JSONObject {
        val salt = hexToBytes(root.getString("saltHex"))
        val iv = hexToBytes(root.getString("ivHex"))
        val cipherBytes = Base64.getDecoder().decode(root.getString("ciphertextBase64"))
        val iterations = root.getInt("iterations")
        val createdAt = root.getLong("createdAt")
        val version = root.getInt("version")

        val passwordChars = password.toCharArray()
        val keyBytes: ByteArray
        try {
            val keySpec = PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            keyBytes = factory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
        } finally {
            passwordChars.fill('\u0000')
        }

        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val aad = "$FORMAT_IDENTIFIER:$version:$createdAt".toByteArray(StandardCharsets.UTF_8)
        cipher.updateAAD(aad)

        val plainBytes = cipher.doFinal(cipherBytes)
        val plainText = String(plainBytes, StandardCharsets.UTF_8)
        return JSONObject(plainText)
    }

    private fun validatePayloadJson(
        payload: JSONObject,
        isEncrypted: Boolean,
        version: Int
    ): BackupValidationResult {
        val customers = payload.optJSONArray("customers") ?: JSONArray()
        val transactions = payload.optJSONArray("transactions") ?: JSONArray()
        val bills = payload.optJSONArray("bills") ?: JSONArray()
        val billItems = payload.optJSONArray("billItems") ?: JSONArray()
        val expenses = payload.optJSONArray("expenses") ?: JSONArray()
        val products = payload.optJSONArray("products") ?: JSONArray()
        val suppliers = payload.optJSONArray("suppliers") ?: JSONArray()
        val supplierTransactions = payload.optJSONArray("supplierTransactions") ?: JSONArray()
        val dailyCashClosings = payload.optJSONArray("dailyCashClosings") ?: JSONArray()

        val customerIds = mutableSetOf<Long>()
        val billIds = mutableSetOf<Long>()
        val billSubtotals = mutableMapOf<Long, Long>()
        val txIds = mutableSetOf<Long>()
        val itemIds = mutableSetOf<Long>()
        val expenseIds = mutableSetOf<Long>()
        val productIds = mutableSetOf<Long>()
        val supplierIds = mutableSetOf<Long>()
        val supplierTxIds = mutableSetOf<Long>()
        val closingDates = mutableSetOf<Long>()

        // 1. Validate Customers
        for (i in 0 until customers.length()) {
            val c = customers.optJSONObject(i) ?: return fail("Malformed customer at index $i")
            val id = c.optLong("id", -1L)
            val name = c.optString("name", "")
            if (id <= 0) return fail("Invalid customer ID: $id")
            if (name.isBlank()) return fail("Customer #$id has empty name")
            if (customerIds.contains(id)) return fail("Duplicate customer ID: $id")
            customerIds.add(id)
        }

        // 2. Validate Products
        for (i in 0 until products.length()) {
            val p = products.optJSONObject(i) ?: return fail("Malformed product at index $i")
            val id = p.optLong("id", -1L)
            val name = p.optString("name", "")
            val sellingPrice = p.optLong("sellingPricePaise", -1L)
            val costPrice = p.optLong("costPricePaise", 0L)
            if (id <= 0) return fail("Invalid product ID: $id")
            if (name.isBlank()) return fail("Product #$id has empty name")
            if (productIds.contains(id)) return fail("Duplicate product ID: $id")
            if (sellingPrice < 0) return fail("Product #$id has negative selling price: $sellingPrice")
            if (costPrice < 0) return fail("Product #$id has negative cost price: $costPrice")
            productIds.add(id)
        }

        // 3. Validate Bills
        val receiptNumbers = mutableSetOf<String>()
        for (i in 0 until bills.length()) {
            val b = bills.optJSONObject(i) ?: return fail("Malformed bill at index $i")
            val id = b.optLong("id", -1L)
            val receiptNumber = b.optString("receiptNumber", "")
            val customerId = if (b.has("customerId") && !b.isNull("customerId")) b.getLong("customerId") else null

            val subtotalPaise = b.optLong("subtotalPaise", -1L)
            val discountPaise = b.optLong("discountPaise", 0L)
            val totalPaise = b.optLong("totalPaise", -1L)
            val paidPaise = b.optLong("amountPaidPaise", -1L)

            val cashPaidPaise = b.optLong("cashPaidPaise", 0L)
            val upiPaidPaise = b.optLong("upiPaidPaise", 0L)
            val otherPaidPaise = b.optLong("otherPaidPaise", 0L)

            if (id <= 0) return fail("Invalid bill ID: $id")
            if (billIds.contains(id)) return fail("Duplicate bill ID: $id")
            billIds.add(id)

            if (receiptNumber.isBlank()) return fail("Bill #$id has empty receipt number")
            if (receiptNumbers.contains(receiptNumber)) return fail("Duplicate receipt number: $receiptNumber")
            receiptNumbers.add(receiptNumber)

            if (customerId != null && !customerIds.contains(customerId)) {
                return fail("Bill #$id references non-existent customer #$customerId")
            }

            if (subtotalPaise < 0) return fail("Bill #$id has negative subtotal: $subtotalPaise")
            if (discountPaise < 0) return fail("Bill #$id has negative discount: $discountPaise")
            if (totalPaise < 0) return fail("Bill #$id has negative total: $totalPaise")
            if (paidPaise < 0) return fail("Bill #$id has negative paid amount: $paidPaise")

            val calculatedTotal = maxOf(0L, subtotalPaise - discountPaise)
            if (totalPaise != calculatedTotal) {
                return fail("Bill #$id total ($totalPaise) does not match subtotal ($subtotalPaise) minus discount ($discountPaise)")
            }

            if (paidPaise > totalPaise) {
                return fail("Bill #$id amount paid ($paidPaise) exceeds total ($totalPaise)")
            }

            if (cashPaidPaise < 0 || upiPaidPaise < 0 || otherPaidPaise < 0) {
                return fail("Bill #$id has negative split payment components")
            }
            if (b.has("cashPaidPaise") || b.has("upiPaidPaise") || b.has("otherPaidPaise")) {
                val splitSum = cashPaidPaise + upiPaidPaise + otherPaidPaise
                if (splitSum != paidPaise) {
                    return fail("Bill #$id split payments do not sum to total amount paid")
                }
            }

            billSubtotals[id] = subtotalPaise
        }

        // 4. Validate Bill Items
        val billItemSums = mutableMapOf<Long, Long>()
        for (i in 0 until billItems.length()) {
            val bi = billItems.optJSONObject(i) ?: return fail("Malformed bill item at index $i")
            val id = bi.optLong("id", -1L)
            val billId = bi.optLong("billId", -1L)
            val quantity = bi.optDouble("quantity", 0.0)
            val unitPrice = bi.optLong("unitPricePaise", -1L)
            val lineTotal = bi.optLong("lineTotalPaise", -1L)
            val productId = if (bi.has("productId") && !bi.isNull("productId")) bi.getLong("productId") else null

            if (id <= 0) return fail("Invalid bill item ID: $id")
            if (itemIds.contains(id)) return fail("Duplicate bill item ID: $id")
            itemIds.add(id)

            if (!billIds.contains(billId)) {
                return fail("Bill item #$id references non-existent bill #$billId")
            }
            if (productId != null && productIds.isNotEmpty() && !productIds.contains(productId)) {
                return fail("Bill item #$id references non-existent product #$productId")
            }
            if (quantity <= 0.0) {
                return fail("Bill item #$id in bill #$billId has non-positive quantity: $quantity")
            }
            if (unitPrice < 0) {
                return fail("Bill item #$id in bill #$billId has negative unit price: $unitPrice")
            }
            if (lineTotal < 0) {
                return fail("Bill item #$id in bill #$billId has negative line total: $lineTotal")
            }

            val expectedLineTotal = Math.round(quantity * unitPrice)
            if (lineTotal != expectedLineTotal) {
                return fail("Bill item #$id line total ($lineTotal) does not match quantity ($quantity) * unitPrice ($unitPrice) = $expectedLineTotal")
            }

            billItemSums[billId] = (billItemSums[billId] ?: 0L) + lineTotal
        }

        for ((billId, sum) in billItemSums) {
            val subtotal = billSubtotals[billId] ?: 0L
            if (sum != subtotal) {
                return fail("Bill #$billId line items sum ($sum) does not match declared subtotal ($subtotal)")
            }
        }

        // 5. Validate Transactions
        val validTypes = setOf(
            TransactionConstants.TYPE_UDHAAR_GIVEN,
            TransactionConstants.TYPE_PAYMENT_RECEIVED,
            TransactionConstants.TYPE_REVERSAL
        )

        for (i in 0 until transactions.length()) {
            val t = transactions.optJSONObject(i) ?: return fail("Malformed transaction at index $i")
            val id = t.optLong("id", -1L)
            val type = t.optString("type", "")
            val customerId = if (t.has("customerId") && !t.isNull("customerId")) t.getLong("customerId") else null
            val amountPaise = t.optLong("amountPaise", -1L)
            val relatedBill = if (t.has("relatedBillId") && !t.isNull("relatedBillId")) t.getLong("relatedBillId") else null

            if (id <= 0) return fail("Invalid transaction ID: $id")
            if (txIds.contains(id)) return fail("Duplicate transaction ID: $id")
            txIds.add(id)

            if (amountPaise <= 0) return fail("Transaction #$id has invalid non-positive amount: $amountPaise")
            if (type !in validTypes) return fail("Transaction #$id has unknown transaction type: $type")

            if (customerId != null && !customerIds.contains(customerId)) {
                return fail("Transaction #$id references non-existent customer #$customerId")
            }
            if (relatedBill != null && !billIds.contains(relatedBill)) {
                return fail("Transaction #$id references non-existent bill #$relatedBill")
            }
        }

        // 6. Validate Reversals
        val reversedTxIds = mutableSetOf<Long>()
        for (i in 0 until transactions.length()) {
            val t = transactions.getJSONObject(i)
            val id = t.getLong("id")
            if (t.has("reversalOfTransactionId") && !t.isNull("reversalOfTransactionId")) {
                val revId = t.getLong("reversalOfTransactionId")
                if (revId == id) return fail("Transaction #$id cannot reverse itself")
                if (!txIds.contains(revId)) return fail("Reversal #$id references non-existent transaction #$revId")
                if (reversedTxIds.contains(revId)) return fail("Duplicate reversal for transaction #$revId")
                reversedTxIds.add(revId)
            }
        }

        // 7. Validate Expenses
        for (i in 0 until expenses.length()) {
            val e = expenses.optJSONObject(i) ?: return fail("Malformed expense at index $i")
            val id = e.optLong("id", -1L)
            val amountPaise = e.optLong("amountPaise", -1L)
            val category = e.optString("category", "")

            if (id <= 0) return fail("Invalid expense ID: $id")
            if (expenseIds.contains(id)) return fail("Duplicate expense ID: $id")
            expenseIds.add(id)

            if (amountPaise <= 0) return fail("Expense #$id has invalid non-positive amount: $amountPaise")
            if (category.isBlank()) return fail("Expense #$id has empty category")
        }

        // 8. Validate Suppliers
        for (i in 0 until suppliers.length()) {
            val s = suppliers.optJSONObject(i) ?: return fail("Malformed supplier at index $i")
            val id = s.optLong("id", -1L)
            val name = s.optString("name", "")
            if (id <= 0) return fail("Invalid supplier ID: $id")
            if (name.isBlank()) return fail("Supplier #$id has empty name")
            if (supplierIds.contains(id)) return fail("Duplicate supplier ID: $id")
            supplierIds.add(id)
        }

        // 9. Validate Supplier Transactions
        for (i in 0 until supplierTransactions.length()) {
            val st = supplierTransactions.optJSONObject(i) ?: return fail("Malformed supplier transaction at index $i")
            val id = st.optLong("id", -1L)
            val sId = st.optLong("supplierId", -1L)
            val amount = st.optLong("amountPaise", -1L)
            val type = st.optString("type", "")
            if (id <= 0) return fail("Invalid supplier transaction ID: $id")
            if (supplierTxIds.contains(id)) return fail("Duplicate supplier transaction ID: $id")
            if (!supplierIds.contains(sId)) return fail("Supplier transaction #$id references non-existent supplier #$sId")
            if (amount <= 0) return fail("Supplier transaction #$id has non-positive amount: $amount")
            if (type != "PURCHASE" && type != "PAYMENT_MADE") return fail("Supplier transaction #$id has invalid type: $type")
            supplierTxIds.add(id)
        }

        // 10. Validate Daily Cash Closings
        for (i in 0 until dailyCashClosings.length()) {
            val dc = dailyCashClosings.optJSONObject(i) ?: return fail("Malformed daily cash closing at index $i")
            val id = dc.optLong("id", -1L)
            val date = dc.optLong("closingDateEpoch", -1L)
            val opening = dc.optLong("openingCashPaise", 0L)
            val sales = dc.optLong("cashSalesPaise", -1L)
            val khata = dc.optLong("cashKhataCollectedPaise", -1L)
            val exp = dc.optLong("cashExpensesPaise", -1L)
            val expected = dc.optLong("expectedCashPaise", Long.MIN_VALUE)
            val counted = dc.optLong("countedCashPaise", Long.MIN_VALUE)
            val diff = dc.optLong("differencePaise", Long.MIN_VALUE)

            if (id <= 0) return fail("Invalid daily cash closing ID: $id")
            if (date <= 0) return fail("Daily cash closing #$id has invalid date")
            if (closingDates.contains(date)) return fail("Duplicate daily cash closing for date: $date")
            if (opening < 0 || sales < 0 || khata < 0 || exp < 0) {
                return fail("Daily cash closing #$id has negative cash figures")
            }
            val calcExpected = opening + (sales + khata) - exp
            if (expected != calcExpected) {
                return fail("Daily cash closing #$id expected ($expected) does not match formula ($calcExpected)")
            }
            if (diff != (counted - expected)) {
                return fail("Daily cash closing #$id difference ($diff) does not match counted ($counted) - expected ($expected)")
            }
            closingDates.add(date)
        }

        return BackupValidationResult(
            isValid = true,
            isEncrypted = isEncrypted,
            version = version,
            customerCount = customers.length(),
            transactionCount = transactions.length(),
            billCount = bills.length(),
            expenseCount = expenses.length(),
            productCount = products.length(),
            supplierCount = suppliers.length(),
            supplierTransactionCount = supplierTransactions.length(),
            dailyClosingCount = dailyCashClosings.length()
        )
    }

    private fun fail(message: String) = BackupValidationResult(
        isValid = false,
        isEncrypted = false,
        version = 0,
        customerCount = 0,
        transactionCount = 0,
        billCount = 0,
        expenseCount = 0,
        errorMessage = message
    )

    private fun parsePayload(root: JSONObject): ParsedBackupData {
        // Shop Profile
        val profile = if (root.has("shopProfile")) {
            val p = root.getJSONObject("shopProfile")
            ShopProfileEntity(
                id = 1,
                shopName = p.optString("shopName", "My Kirana Store"),
                address = p.optString("address", ""),
                phone = p.optString("phone", ""),
                upiId = p.optString("upiId", ""),
                receiptFooter = p.optString("receiptFooter", "Thank you! Visit again."),
                language = p.optString("language", "en")
            )
        } else null

        // Customers
        val customerList = mutableListOf<CustomerEntity>()
        val cArray = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until cArray.length()) {
            val o = cArray.getJSONObject(i)
            customerList.add(
                CustomerEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    archivedAt = if (o.has("archivedAt") && !o.isNull("archivedAt")) o.getLong("archivedAt") else null,
                    dueDate = if (o.has("dueDate") && !o.isNull("dueDate")) o.getLong("dueDate") else null
                )
            )
        }

        // Transactions
        val transactionList = mutableListOf<FinancialTransactionEntity>()
        val tArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until tArray.length()) {
            val o = tArray.getJSONObject(i)
            transactionList.add(
                FinancialTransactionEntity(
                    id = o.getLong("id"),
                    type = o.getString("type"),
                    customerId = if (o.has("customerId") && !o.isNull("customerId")) o.getLong("customerId") else null,
                    amountPaise = o.getLong("amountPaise"),
                    paymentMethod = o.optString("paymentMethod", TransactionConstants.METHOD_CASH),
                    description = o.optString("description", ""),
                    occurredAt = o.optLong("occurredAt", System.currentTimeMillis()),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    reversalOfTransactionId = if (o.has("reversalOfTransactionId") && !o.isNull("reversalOfTransactionId")) o.getLong("reversalOfTransactionId") else null,
                    relatedBillId = if (o.has("relatedBillId") && !o.isNull("relatedBillId")) o.getLong("relatedBillId") else null,
                    idempotencyKey = o.optString("idempotencyKey", java.util.UUID.randomUUID().toString()),
                    status = o.optString("status", TransactionConstants.STATUS_ACTIVE),
                    dueDate = if (o.has("dueDate") && !o.isNull("dueDate")) o.getLong("dueDate") else null
                )
            )
        }

        // Bills
        val billList = mutableListOf<BillEntity>()
        val bArray = root.optJSONArray("bills") ?: JSONArray()
        for (i in 0 until bArray.length()) {
            val o = bArray.getJSONObject(i)
            billList.add(
                BillEntity(
                    id = o.getLong("id"),
                    receiptNumber = o.getString("receiptNumber"),
                    customerId = if (o.has("customerId") && !o.isNull("customerId")) o.getLong("customerId") else null,
                    subtotalPaise = o.getLong("subtotalPaise"),
                    discountPaise = o.optLong("discountPaise", 0L),
                    totalPaise = o.getLong("totalPaise"),
                    amountPaidPaise = o.getLong("amountPaidPaise"),
                    paymentMethod = o.optString("paymentMethod", TransactionConstants.METHOD_CASH),
                    billType = o.optString("billType", BillConstants.TYPE_QUICK),
                    notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    status = o.optString("status", BillConstants.STATUS_COMPLETED),
                    cashPaidPaise = o.optLong("cashPaidPaise", 0L),
                    upiPaidPaise = o.optLong("upiPaidPaise", 0L),
                    otherPaidPaise = o.optLong("otherPaidPaise", 0L),
                    idempotencyKey = o.optString("idempotencyKey", "")
                )
            )
        }

        // Bill Items
        val itemList = mutableListOf<BillItemEntity>()
        val biArray = root.optJSONArray("billItems") ?: JSONArray()
        for (i in 0 until biArray.length()) {
            val o = biArray.getJSONObject(i)
            itemList.add(
                BillItemEntity(
                    id = o.getLong("id"),
                    billId = o.getLong("billId"),
                    description = o.getString("description"),
                    quantity = o.optDouble("quantity", 1.0),
                    unitPricePaise = o.getLong("unitPricePaise"),
                    lineTotalPaise = o.getLong("lineTotalPaise"),
                    productId = if (o.has("productId") && !o.isNull("productId")) o.getLong("productId") else null
                )
            )
        }

        // Expenses
        val expenseList = mutableListOf<ExpenseEntity>()
        val eArray = root.optJSONArray("expenses") ?: JSONArray()
        for (i in 0 until eArray.length()) {
            val o = eArray.getJSONObject(i)
            expenseList.add(
                ExpenseEntity(
                    id = o.getLong("id"),
                    amountPaise = o.getLong("amountPaise"),
                    category = o.optString("category", ExpenseConstants.CAT_OTHER),
                    description = o.optString("description", ""),
                    paymentMethod = o.optString("paymentMethod", TransactionConstants.METHOD_CASH),
                    occurredAt = o.optLong("occurredAt", System.currentTimeMillis()),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    status = o.optString("status", ExpenseConstants.STATUS_ACTIVE)
                )
            )
        }

        // Products
        val productList = mutableListOf<ProductEntity>()
        val pArray = root.optJSONArray("products") ?: JSONArray()
        for (i in 0 until pArray.length()) {
            val o = pArray.getJSONObject(i)
            productList.add(
                ProductEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    unit = o.optString("unit", "pcs"),
                    sellingPricePaise = o.getLong("sellingPricePaise"),
                    costPricePaise = o.optLong("costPricePaise", 0L),
                    stockQuantity = o.optDouble("stockQuantity", 0.0),
                    lowStockThreshold = o.optDouble("lowStockThreshold", 5.0),
                    barcode = o.optString("barcode", ""),
                    category = o.optString("category", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    isArchived = o.optBoolean("isArchived", false)
                )
            )
        }

        // Suppliers
        val supplierList = mutableListOf<SupplierEntity>()
        val sArray = root.optJSONArray("suppliers") ?: JSONArray()
        for (i in 0 until sArray.length()) {
            val o = sArray.getJSONObject(i)
            supplierList.add(
                SupplierEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    archivedAt = if (o.has("archivedAt") && !o.isNull("archivedAt")) o.getLong("archivedAt") else null
                )
            )
        }

        // Supplier Transactions
        val supplierTxList = mutableListOf<SupplierTransactionEntity>()
        val stArray = root.optJSONArray("supplierTransactions") ?: JSONArray()
        for (i in 0 until stArray.length()) {
            val o = stArray.getJSONObject(i)
            supplierTxList.add(
                SupplierTransactionEntity(
                    id = o.getLong("id"),
                    supplierId = o.getLong("supplierId"),
                    type = o.getString("type"),
                    amountPaise = o.getLong("amountPaise"),
                    paymentMethod = o.optString("paymentMethod", TransactionConstants.METHOD_CASH),
                    billInvoiceNumber = o.optString("billInvoiceNumber", ""),
                    notes = o.optString("notes", ""),
                    occurredAt = o.optLong("occurredAt", System.currentTimeMillis()),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    reversalOfTransactionId = if (o.has("reversalOfTransactionId") && !o.isNull("reversalOfTransactionId")) o.getLong("reversalOfTransactionId") else null,
                    status = o.optString("status", "ACTIVE"),
                    idempotencyKey = o.optString("idempotencyKey", "")
                )
            )
        }

        // Daily Cash Closings
        val closingList = mutableListOf<DailyCashClosingEntity>()
        val dcArray = root.optJSONArray("dailyCashClosings") ?: JSONArray()
        for (i in 0 until dcArray.length()) {
            val o = dcArray.getJSONObject(i)
            closingList.add(
                DailyCashClosingEntity(
                    id = o.getLong("id"),
                    closingDateEpoch = o.getLong("closingDateEpoch"),
                    openingCashPaise = o.optLong("openingCashPaise", 0L),
                    cashSalesPaise = o.getLong("cashSalesPaise"),
                    cashKhataCollectedPaise = o.getLong("cashKhataCollectedPaise"),
                    cashExpensesPaise = o.getLong("cashExpensesPaise"),
                    expectedCashPaise = o.getLong("expectedCashPaise"),
                    countedCashPaise = o.getLong("countedCashPaise"),
                    differencePaise = o.getLong("differencePaise"),
                    notes = o.optString("notes", ""),
                    closedAt = o.optLong("closedAt", System.currentTimeMillis())
                )
            )
        }

        return ParsedBackupData(
            profile = profile,
            customers = customerList,
            transactions = transactionList,
            bills = billList,
            billItems = itemList,
            expenses = expenseList,
            products = productList,
            suppliers = supplierList,
            supplierTransactions = supplierTxList,
            dailyCashClosings = closingList
        )
    }

    /**
     * Atomically executes the restore inside a transaction runner.
     * Rolls back completely if any database error or constraint violation occurs.
     */
    suspend fun restoreVerifiedData(data: ParsedBackupData): Boolean = withContext(Dispatchers.IO) {
        transactionRunner {
            // Delete dependent records first to satisfy foreign keys
            database.supplierDao().deleteAllTransactions()
            database.supplierDao().deleteAllSuppliers()
            database.productDao().deleteAllProducts()
            database.dailyCashClosingDao().deleteAllClosings()
            database.billDao().deleteAllBillItems()
            database.financialTransactionDao().deleteAllTransactions()
            database.billDao().deleteAllBills()
            database.expenseDao().deleteAllExpenses()
            database.customerDao().deleteAllCustomers()

            // Restore Profile
            if (data.profile != null) {
                database.shopProfileDao().saveShopProfile(data.profile)
            }

            // Restore Customers, Bills, Items, Transactions, Expenses
            database.customerDao().restoreAllCustomers(data.customers)
            database.billDao().restoreAllBills(data.bills)
            database.billDao().restoreAllBillItems(data.billItems)
            database.financialTransactionDao().restoreAllTransactions(data.transactions)
            database.expenseDao().restoreAllExpenses(data.expenses)

            // Restore Products, Suppliers, Supplier Transactions, Daily Cash Closings
            database.productDao().restoreAllProducts(data.products)
            database.supplierDao().restoreAllSuppliers(data.suppliers)
            database.supplierDao().restoreAllTransactions(data.supplierTransactions)
            database.dailyCashClosingDao().restoreAllClosings(data.dailyCashClosings)

            // Post-restore record count verification
            val actualCustomerCount = database.customerDao().getAllCustomersRaw().size
            if (actualCustomerCount != data.customers.size) {
                throw IllegalStateException("Post-restore customer count verification failed: expected ${data.customers.size}, found $actualCustomerCount")
            }

            val actualBillCount = database.billDao().getAllBillsRaw().size
            if (actualBillCount != data.bills.size) {
                throw IllegalStateException("Post-restore bill count verification failed: expected ${data.bills.size}, found $actualBillCount")
            }

            val actualBillItemCount = database.billDao().getAllBillItemsRaw().size
            if (actualBillItemCount != data.billItems.size) {
                throw IllegalStateException("Post-restore bill item count verification failed: expected ${data.billItems.size}, found $actualBillItemCount")
            }

            val actualTxCount = database.financialTransactionDao().getAllTransactionsRaw().size
            if (actualTxCount != data.transactions.size) {
                throw IllegalStateException("Post-restore transaction count verification failed: expected ${data.transactions.size}, found $actualTxCount")
            }

            val actualExpenseCount = database.expenseDao().getAllExpensesRaw().size
            if (actualExpenseCount != data.expenses.size) {
                throw IllegalStateException("Post-restore expense count verification failed: expected ${data.expenses.size}, found $actualExpenseCount")
            }

            val actualProductCount = database.productDao().getAllProductsRaw().size
            if (actualProductCount != data.products.size) {
                throw IllegalStateException("Post-restore product count verification failed: expected ${data.products.size}, found $actualProductCount")
            }

            val actualSupplierCount = database.supplierDao().getAllSuppliersRaw().size
            if (actualSupplierCount != data.suppliers.size) {
                throw IllegalStateException("Post-restore supplier count verification failed: expected ${data.suppliers.size}, found $actualSupplierCount")
            }

            val actualSupplierTxCount = database.supplierDao().getAllTransactionsRaw().size
            if (actualSupplierTxCount != data.supplierTransactions.size) {
                throw IllegalStateException("Post-restore supplier transaction count verification failed: expected ${data.supplierTransactions.size}, found $actualSupplierTxCount")
            }

            val actualClosingCount = database.dailyCashClosingDao().getAllClosingsRaw().size
            if (actualClosingCount != data.dailyCashClosings.size) {
                throw IllegalStateException("Post-restore daily closing count verification failed: expected ${data.dailyCashClosings.size}, found $actualClosingCount")
            }

            // Post-restore financial invariant verification: customer ledger balances
            for (c in data.customers) {
                val expectedBalance = data.transactions
                    .filter { it.customerId == c.id && it.status == TransactionConstants.STATUS_ACTIVE }
                    .fold(0L) { acc, tx ->
                        when (tx.type) {
                            TransactionConstants.TYPE_UDHAAR_GIVEN -> acc + tx.amountPaise
                            TransactionConstants.TYPE_PAYMENT_RECEIVED -> acc - tx.amountPaise
                            else -> acc
                        }
                    }
                val liveBalance = database.customerDao().getCustomerWithBalance(c.id)?.balancePaise ?: 0L
                if (liveBalance != expectedBalance) {
                    throw IllegalStateException("Post-restore financial invariant failed: customer balance mismatch for customer ID ${c.id} (expected $expectedBalance, live $liveBalance)")
                }
            }

            // Post-restore financial invariant verification: supplier ledger balances
            for (s in data.suppliers) {
                val expectedSupplierBalance = data.supplierTransactions
                    .filter { it.supplierId == s.id && it.status == "ACTIVE" }
                    .fold(0L) { acc, tx ->
                        when (tx.type) {
                            "PURCHASE" -> acc + tx.amountPaise
                            "PAYMENT_MADE" -> acc - tx.amountPaise
                            else -> acc
                        }
                    }
                val liveSupplierBalance = database.supplierDao().getSupplierWithBalance(s.id)?.balancePaise ?: 0L
                if (liveSupplierBalance != expectedSupplierBalance) {
                    throw IllegalStateException("Post-restore supplier balance invariant failed for supplier ${s.id} ('${s.name}'): expected $expectedSupplierBalance, live $liveSupplierBalance")
                }
            }
        }
        true
    }

    private fun readBoundedString(inputStream: InputStream, maxBytes: Long): String {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalBytes = 0L
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            totalBytes += read
            if (totalBytes > maxBytes) {
                throw IllegalArgumentException("Backup file exceeds maximum allowed size (${maxBytes / (1024 * 1024)} MB)")
            }
            baos.write(buffer, 0, read)
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private fun canonicalJsonString(obj: Any): String {
        return when (obj) {
            is JSONObject -> {
                val keys = obj.keys().asSequence().sorted().toList()
                keys.joinToString(prefix = "{", postfix = "}", separator = ",") { k ->
                    "\"$k\":" + canonicalJsonString(obj.get(k))
                }
            }
            is JSONArray -> {
                (0 until obj.length()).joinToString(prefix = "[", postfix = "]", separator = ",") { i ->
                    canonicalJsonString(obj.get(i))
                }
            }
            is String -> JSONObject.quote(obj)
            else -> obj.toString()
        }
    }

    private fun calculateSha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(bytes)
    }

    private fun isValidHex(hex: String): Boolean {
        return hex.isNotEmpty() && hex.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
