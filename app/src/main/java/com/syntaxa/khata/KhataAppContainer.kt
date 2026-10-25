package com.syntaxa.khata

import android.content.Context
import com.syntaxa.khata.data.local.KhataDatabase
import com.syntaxa.khata.data.repository.BillingRepository
import com.syntaxa.khata.data.repository.CustomerRepository
import com.syntaxa.khata.data.repository.DailyClosingRepository
import com.syntaxa.khata.data.repository.DailyReportRepository
import com.syntaxa.khata.data.repository.ExpenseRepository
import com.syntaxa.khata.data.repository.FinancialRepository
import com.syntaxa.khata.data.repository.InventoryRepository
import com.syntaxa.khata.data.repository.ShopProfileRepository
import com.syntaxa.khata.data.repository.SupplierRepository

class KhataAppContainer(private val context: Context) {
    val database: KhataDatabase by lazy {
        KhataDatabase.getInstance(context)
    }

    val customerRepository: CustomerRepository by lazy {
        CustomerRepository(database.customerDao())
    }

    val financialRepository: FinancialRepository by lazy {
        FinancialRepository(database.financialTransactionDao(), database.customerDao())
    }

    val billingRepository: BillingRepository by lazy {
        BillingRepository(
            billDao = database.billDao(),
            financialTransactionDao = database.financialTransactionDao(),
            database = database,
            productDao = database.productDao()
        )
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database.expenseDao())
    }

    val shopProfileRepository: ShopProfileRepository by lazy {
        ShopProfileRepository(database.shopProfileDao())
    }

    val dailyReportRepository: DailyReportRepository by lazy {
        DailyReportRepository(
            billingRepository = billingRepository,
            financialRepository = financialRepository,
            expenseRepository = expenseRepository,
            customerDao = database.customerDao()
        )
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(database.productDao())
    }

    val supplierRepository: SupplierRepository by lazy {
        SupplierRepository(database.supplierDao())
    }

    val dailyClosingRepository: DailyClosingRepository by lazy {
        DailyClosingRepository(database.dailyCashClosingDao(), dailyReportRepository)
    }

    val subscriptionManager: com.syntaxa.khata.feature.subscription.SubscriptionManager by lazy {
        com.syntaxa.khata.feature.subscription.SubscriptionManager(context)
    }
}
