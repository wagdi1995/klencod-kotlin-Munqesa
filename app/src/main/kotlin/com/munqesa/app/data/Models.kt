package com.munqesa.app.data

data class Product(val id: Long, val name: String, val price: Double) {
    // يستخدمه ArrayAdapter لعرض الاسم في القائمة المنسدلة
    override fun toString(): String = name
}

data class InvoiceItem(
    val productId: Long,
    val name: String,
    val unitPrice: Double,
    val quantity: Int
) {
    val subtotal: Double get() = unitPrice * quantity
}

data class Invoice(
    val id: Long,
    val customer: String,
    val total: Double,
    val createdAt: Long,
    val itemCount: Int
)

data class Report(
    val id: Long,
    val invoiceId: Long,
    val header: String,
    val customer: String,
    val total: Double,
    val createdAt: Long
)
