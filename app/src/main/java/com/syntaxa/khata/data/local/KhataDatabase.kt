package com.syntaxa.khata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.syntaxa.khata.data.local.dao.BillDao
import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.dao.DailyCashClosingDao
import com.syntaxa.khata.data.local.dao.ExpenseDao
import com.syntaxa.khata.data.local.dao.FinancialTransactionDao
import com.syntaxa.khata.data.local.dao.ProductDao
import com.syntaxa.khata.data.local.dao.ShopProfileDao
import com.syntaxa.khata.data.local.dao.SupplierDao
import com.syntaxa.khata.data.local.entity.AppSettingsEntity
import com.syntaxa.khata.data.local.entity.BillEntity
import com.syntaxa.khata.data.local.entity.BillItemEntity
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.data.local.entity.DailyCashClosingEntity
import com.syntaxa.khata.data.local.entity.ExpenseEntity
import com.syntaxa.khata.data.local.entity.FinancialTransactionEntity
import com.syntaxa.khata.data.local.entity.ProductEntity
import com.syntaxa.khata.data.local.entity.ShopProfileEntity
import com.syntaxa.khata.data.local.entity.SupplierEntity
import com.syntaxa.khata.data.local.entity.SupplierTransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface KhataDatabaseSource {
    fun shopProfileDao(): ShopProfileDao
    fun customerDao(): CustomerDao
    fun financialTransactionDao(): FinancialTransactionDao
    fun billDao(): BillDao
    fun expenseDao(): ExpenseDao
    fun productDao(): ProductDao
    fun supplierDao(): SupplierDao
    fun dailyCashClosingDao(): DailyCashClosingDao
}

@Database(
    entities = [
        ShopProfileEntity::class,
        CustomerEntity::class,
        FinancialTransactionEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        ExpenseEntity::class,
        AppSettingsEntity::class,
        ProductEntity::class,
        SupplierEntity::class,
        SupplierTransactionEntity::class,
        DailyCashClosingEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class KhataDatabase : RoomDatabase(), KhataDatabaseSource {
    abstract override fun shopProfileDao(): ShopProfileDao
    abstract override fun customerDao(): CustomerDao
    abstract override fun financialTransactionDao(): FinancialTransactionDao
    abstract override fun billDao(): BillDao
    abstract override fun expenseDao(): ExpenseDao
    abstract override fun productDao(): ProductDao
    abstract override fun supplierDao(): SupplierDao
    abstract override fun dailyCashClosingDao(): DailyCashClosingDao

    companion object {
        @Volatile
        private var INSTANCE: KhataDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN cashPaidPaise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN upiPaidPaise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN otherPaidPaise INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_idempotencyKey ON bills(idempotencyKey)")

                // Backfill split amount values according to recorded paymentMethod
                db.execSQL("UPDATE bills SET cashPaidPaise = amountPaidPaise WHERE paymentMethod = 'CASH'")
                db.execSQL("UPDATE bills SET upiPaidPaise = amountPaidPaise WHERE paymentMethod = 'UPI'")
                db.execSQL("UPDATE bills SET otherPaidPaise = amountPaidPaise WHERE paymentMethod NOT IN ('CASH', 'UPI')")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Due Dates & Bill Item Product Linkage
                db.execSQL("ALTER TABLE customers ADD COLUMN dueDate INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_dueDate ON customers(dueDate)")
                db.execSQL("ALTER TABLE financial_transactions ADD COLUMN dueDate INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_financial_transactions_dueDate ON financial_transactions(dueDate)")
                db.execSQL("ALTER TABLE bill_items ADD COLUMN productId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_items_productId ON bill_items(productId)")

                // 2. Simple Inventory
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        unit TEXT NOT NULL DEFAULT 'pcs',
                        sellingPricePaise INTEGER NOT NULL DEFAULT 0,
                        costPricePaise INTEGER NOT NULL DEFAULT 0,
                        stockQuantity REAL NOT NULL DEFAULT 0.0,
                        lowStockThreshold REAL NOT NULL DEFAULT 5.0,
                        barcode TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")

                // 3. Suppliers & Supplier Transactions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS suppliers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        archivedAt INTEGER DEFAULT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_name ON suppliers(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_phone ON suppliers(phone)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS supplier_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        supplierId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        billInvoiceNumber TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        occurredAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        reversalOfTransactionId INTEGER DEFAULT NULL,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        idempotencyKey TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_supplierId ON supplier_transactions(supplierId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_occurredAt ON supplier_transactions(occurredAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_transactions_type ON supplier_transactions(type)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_transactions_idempotencyKey ON supplier_transactions(idempotencyKey)")

                // 4. Daily Cash Closings
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_cash_closings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        closingDateEpoch INTEGER NOT NULL,
                        openingCashPaise INTEGER NOT NULL DEFAULT 0,
                        cashSalesPaise INTEGER NOT NULL,
                        cashKhataCollectedPaise INTEGER NOT NULL,
                        cashExpensesPaise INTEGER NOT NULL,
                        expectedCashPaise INTEGER NOT NULL,
                        countedCashPaise INTEGER NOT NULL,
                        differencePaise INTEGER NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        closedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_cash_closings_closingDateEpoch ON daily_cash_closings(closingDateEpoch)")
            }
        }

        fun getInstance(context: Context): KhataDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhataDatabase::class.java,
                    "syntaxa_khata.db"
                ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate clean initial empty ShopProfile
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.shopProfileDao()?.saveShopProfile(
                                ShopProfileEntity(
                                    id = 1,
                                    shopName = "",
                                    receiptFooter = ""
                                )
                            )
                        }
                    }
                }).build().also { INSTANCE = it }
            }
        }
    }
}
