package com.syntaxa.khata

import androidx.sqlite.db.SupportSQLiteDatabase
import com.syntaxa.khata.data.local.KhataDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RoomMigrationIntegrityTest {

    private fun createCaptureDatabase(capturedSql: MutableList<String>): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                capturedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase
    }

    @Test
    fun testMigration1To2_addsSplitPaymentAndIdempotencyColumns() {
        val captured = mutableListOf<String>()
        val db = createCaptureDatabase(captured)

        assertEquals(1, KhataDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, KhataDatabase.MIGRATION_1_2.endVersion)

        KhataDatabase.MIGRATION_1_2.migrate(db)

        assertTrue(captured.any { it.contains("ALTER TABLE bills ADD COLUMN cashPaidPaise") })
        assertTrue(captured.any { it.contains("ALTER TABLE bills ADD COLUMN upiPaidPaise") })
        assertTrue(captured.any { it.contains("ALTER TABLE bills ADD COLUMN otherPaidPaise") })
        assertTrue(captured.any { it.contains("ALTER TABLE bills ADD COLUMN idempotencyKey") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_bills_idempotencyKey") })
        assertTrue(captured.any { it.contains("UPDATE bills SET cashPaidPaise = amountPaidPaise WHERE paymentMethod = 'CASH'") })
        assertTrue(captured.any { it.contains("UPDATE bills SET upiPaidPaise = amountPaidPaise WHERE paymentMethod = 'UPI'") })
    }

    @Test
    fun testMigration2To3_addsDueDatesInventorySuppliersAndCashClosings() {
        val captured = mutableListOf<String>()
        val db = createCaptureDatabase(captured)

        assertEquals(2, KhataDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, KhataDatabase.MIGRATION_2_3.endVersion)

        KhataDatabase.MIGRATION_2_3.migrate(db)

        // 1. Due Dates
        assertTrue(captured.any { it.contains("ALTER TABLE customers ADD COLUMN dueDate") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_customers_dueDate") })
        assertTrue(captured.any { it.contains("ALTER TABLE financial_transactions ADD COLUMN dueDate") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_financial_transactions_dueDate") })

        // 2. Bill Items Product linkage
        assertTrue(captured.any { it.contains("ALTER TABLE bill_items ADD COLUMN productId") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_bill_items_productId") })

        // 3. Products
        assertTrue(captured.any { it.contains("CREATE TABLE IF NOT EXISTS products") && it.contains("sellingPricePaise") && it.contains("stockQuantity") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_products_name") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_products_barcode") })

        // 4. Suppliers & Supplier Transactions
        assertTrue(captured.any { it.contains("CREATE TABLE IF NOT EXISTS suppliers") })
        assertTrue(captured.any { it.contains("CREATE TABLE IF NOT EXISTS supplier_transactions") && it.contains("supplierId") })
        assertTrue(captured.any { it.contains("CREATE INDEX IF NOT EXISTS index_supplier_transactions_supplierId") })

        // 5. Daily Cash Closings with Opening Cash
        assertTrue(captured.any { it.contains("CREATE TABLE IF NOT EXISTS daily_cash_closings") && it.contains("openingCashPaise") })
        assertTrue(captured.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_cash_closings_closingDateEpoch") })
    }
}
