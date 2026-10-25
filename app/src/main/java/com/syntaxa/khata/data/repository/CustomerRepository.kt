package com.syntaxa.khata.data.repository

import com.syntaxa.khata.data.local.dao.CustomerDao
import com.syntaxa.khata.data.local.entity.CustomerEntity
import com.syntaxa.khata.domain.model.CustomerWithBalance
import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val customerDao: CustomerDao) {
    fun observeActiveCustomers(): Flow<List<CustomerWithBalance>> =
        customerDao.observeActiveCustomersWithBalance()

    fun searchCustomers(query: String): Flow<List<CustomerWithBalance>> =
        customerDao.searchActiveCustomersWithBalance(query)

    fun observeCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> =
        customerDao.observeCustomerWithBalance(id)

    suspend fun getCustomerWithBalance(id: Long): CustomerWithBalance? =
        customerDao.getCustomerWithBalance(id)

    suspend fun getCustomerById(id: Long): CustomerEntity? =
        customerDao.getCustomerById(id)

    fun observeArchivedCustomers(): Flow<List<CustomerWithBalance>> =
        customerDao.observeArchivedCustomersWithBalance()

    suspend fun createCustomer(name: String, phone: String = "", notes: String = "", dueDate: Long? = null): Long {
        require(name.isNotBlank()) { "Customer name is required" }
        val customer = CustomerEntity(
            name = name.trim(),
            phone = phone.trim(),
            notes = notes.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            dueDate = dueDate
        )
        return customerDao.insertCustomer(customer)
    }

    suspend fun updateDueDate(customerId: Long, dueDate: Long?) {
        customerDao.updateCustomerDueDate(customerId, dueDate)
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        require(customer.name.isNotBlank()) { "Customer name is required" }
        customerDao.updateCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun archiveCustomer(customerId: Long) {
        customerDao.setCustomerArchived(customerId, System.currentTimeMillis())
    }

    suspend fun restoreCustomer(customerId: Long) {
        customerDao.setCustomerArchived(customerId, null)
    }

    suspend fun isPhoneDuplicate(phone: String, excludeId: Long? = null): Boolean {
        if (phone.isBlank()) return false
        return customerDao.countByPhone(phone.trim(), excludeId) > 0
    }

    fun observeTotalMarketUdhaar(): Flow<Long> =
        customerDao.observeTotalMarketUdhaar()

    suspend fun getAllCustomersRaw(): List<CustomerEntity> =
        customerDao.getAllCustomersRaw()
}
