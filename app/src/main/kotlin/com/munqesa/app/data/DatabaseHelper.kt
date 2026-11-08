package com.munqesa.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * نفس مخطط قاعدة البيانات القديم (munqesa.db) حتى تبقى بيانات المستخدمين كما هي.
 */
class DatabaseHelper private constructor(private val context: Context) {

    companion object {
        const val DATABASE_NAME = "munqesa.db"

        const val TABLE_PRODUCTS = "products"
        const val COL_PRODUCT_ID = "id"
        const val COL_PRODUCT_NAME = "name"
        const val COL_PRODUCT_PRICE = "price"

        const val TABLE_INVOICES = "invoices"
        const val COL_INVOICE_ID = "id"
        const val COL_INVOICE_CUSTOMER = "customer_name"
        const val COL_INVOICE_TOTAL = "total_amount"
        const val COL_INVOICE_CREATED = "created_at"

        const val TABLE_INVOICE_ITEMS = "invoice_items"
        const val COL_ITEM_ID = "id"
        const val COL_ITEM_INVOICE_ID = "invoice_id"
        const val COL_ITEM_PRODUCT_ID = "product_id"
        const val COL_ITEM_PRODUCT_NAME = "product_name"
        const val COL_ITEM_UNIT_PRICE = "unit_price"
        const val COL_ITEM_QUANTITY = "quantity"
        const val COL_ITEM_SUBTOTAL = "subtotal"

        const val TABLE_REPORTS = "reports"
        const val COL_REPORT_ID = "id"
        const val COL_REPORT_INVOICE_ID = "invoice_id"
        const val COL_REPORT_HEADER = "header"
        const val COL_REPORT_CUSTOMER = "customer_name"
        const val COL_REPORT_TOTAL = "total_amount"
        const val COL_REPORT_CREATED = "created_at"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun get(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
    }

    private var db: SQLiteDatabase? = null

    @Synchronized
    fun getDatabase(): SQLiteDatabase {
        var current = db
        if (current == null || !current.isOpen) {
            try {
                val dbPath = File(context.filesDir.parentFile, "databases/$DATABASE_NAME")
                dbPath.parentFile?.mkdirs()
                current = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
                current.enableWriteAheadLogging()
                createTables(current)
                db = current
            } catch (e: Exception) {
                throw RuntimeException("Failed to open database: ${e.message}", e)
            }
        }
        return current!!
    }

    private fun createTables(database: SQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_PRODUCTS (
                $COL_PRODUCT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PRODUCT_NAME TEXT NOT NULL,
                $COL_PRODUCT_PRICE REAL NOT NULL
            )"""
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_INVOICES (
                $COL_INVOICE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_INVOICE_CUSTOMER TEXT NOT NULL,
                $COL_INVOICE_TOTAL REAL NOT NULL,
                $COL_INVOICE_CREATED INTEGER NOT NULL
            )"""
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_INVOICE_ITEMS (
                $COL_ITEM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ITEM_INVOICE_ID INTEGER NOT NULL,
                $COL_ITEM_PRODUCT_ID INTEGER NOT NULL,
                $COL_ITEM_PRODUCT_NAME TEXT NOT NULL,
                $COL_ITEM_UNIT_PRICE REAL NOT NULL,
                $COL_ITEM_QUANTITY INTEGER NOT NULL,
                $COL_ITEM_SUBTOTAL REAL NOT NULL,
                FOREIGN KEY($COL_ITEM_INVOICE_ID) REFERENCES $TABLE_INVOICES($COL_INVOICE_ID)
            )"""
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS $TABLE_REPORTS (
                $COL_REPORT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_REPORT_INVOICE_ID INTEGER NOT NULL,
                $COL_REPORT_HEADER TEXT,
                $COL_REPORT_CUSTOMER TEXT NOT NULL,
                $COL_REPORT_TOTAL REAL NOT NULL,
                $COL_REPORT_CREATED INTEGER NOT NULL
            )"""
        )
        createIndexes(database)
    }

    private fun createIndexes(database: SQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_items_invoice ON $TABLE_INVOICE_ITEMS($COL_ITEM_INVOICE_ID)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_invoice ON $TABLE_REPORTS($COL_REPORT_INVOICE_ID)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_reports_created ON $TABLE_REPORTS($COL_REPORT_CREATED)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_invoices_created ON $TABLE_INVOICES($COL_INVOICE_CREATED)")
    }

    // ---------------- Products ----------------

    fun getProducts(query: String = ""): List<Product> {
        val list = mutableListOf<Product>()
        val q = query.trim()
        val cursor = if (q.isEmpty()) {
            getDatabase().query(TABLE_PRODUCTS, null, null, null, null, null, "$COL_PRODUCT_ID ASC")
        } else {
            getDatabase().query(
                TABLE_PRODUCTS, null, "$COL_PRODUCT_NAME LIKE ?", arrayOf("%$q%"),
                null, null, "$COL_PRODUCT_ID ASC"
            )
        }
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(COL_PRODUCT_ID)
            val iName = c.getColumnIndexOrThrow(COL_PRODUCT_NAME)
            val iPrice = c.getColumnIndexOrThrow(COL_PRODUCT_PRICE)
            while (c.moveToNext()) {
                list.add(Product(c.getLong(iId), c.getString(iName), c.getDouble(iPrice)))
            }
        }
        return list
    }

    fun insertProduct(name: String, price: Double): Long {
        val values = ContentValues().apply {
            put(COL_PRODUCT_NAME, name)
            put(COL_PRODUCT_PRICE, price)
        }
        return getDatabase().insert(TABLE_PRODUCTS, null, values)
    }

    fun insertProducts(products: List<Pair<String, Double>>) {
        val database = getDatabase()
        database.beginTransaction()
        try {
            for ((name, price) in products) {
                val values = ContentValues().apply {
                    put(COL_PRODUCT_NAME, name)
                    put(COL_PRODUCT_PRICE, price)
                }
                database.insert(TABLE_PRODUCTS, null, values)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun updateProduct(id: Long, name: String, price: Double) {
        val values = ContentValues().apply {
            put(COL_PRODUCT_NAME, name)
            put(COL_PRODUCT_PRICE, price)
        }
        getDatabase().update(TABLE_PRODUCTS, values, "$COL_PRODUCT_ID = ?", arrayOf(id.toString()))
    }

    fun deleteProduct(id: Long) {
        getDatabase().delete(TABLE_PRODUCTS, "$COL_PRODUCT_ID = ?", arrayOf(id.toString()))
    }

    fun deleteAllProducts() {
        getDatabase().delete(TABLE_PRODUCTS, null, null)
    }

    // ---------------- Invoices ----------------

    private fun insertItems(database: SQLiteDatabase, invoiceId: Long, items: List<InvoiceItem>) {
        for (item in items) {
            val v = ContentValues().apply {
                put(COL_ITEM_INVOICE_ID, invoiceId)
                put(COL_ITEM_PRODUCT_ID, item.productId)
                put(COL_ITEM_PRODUCT_NAME, item.name)
                put(COL_ITEM_UNIT_PRICE, item.unitPrice)
                put(COL_ITEM_QUANTITY, item.quantity)
                put(COL_ITEM_SUBTOTAL, item.subtotal)
            }
            database.insert(TABLE_INVOICE_ITEMS, null, v)
        }
    }

    fun insertInvoice(customer: String, items: List<InvoiceItem>): Long {
        val database = getDatabase()
        val total = items.sumOf { it.subtotal }
        database.beginTransaction()
        try {
            val invoiceValues = ContentValues().apply {
                put(COL_INVOICE_CUSTOMER, customer)
                put(COL_INVOICE_TOTAL, total)
                put(COL_INVOICE_CREATED, System.currentTimeMillis())
            }
            val invoiceId = database.insert(TABLE_INVOICES, null, invoiceValues)
            insertItems(database, invoiceId, items)
            database.setTransactionSuccessful()
            return invoiceId
        } finally {
            database.endTransaction()
        }
    }

    /** يحفظ الفاتورة وتقريرها (عرض السعر) في عملية واحدة. */
    fun saveInvoiceWithReport(customer: String, items: List<InvoiceItem>): Long {
        val database = getDatabase()
        val total = items.sumOf { it.subtotal }
        val now = System.currentTimeMillis()
        database.beginTransaction()
        try {
            val invoiceId = database.insert(TABLE_INVOICES, null, ContentValues().apply {
                put(COL_INVOICE_CUSTOMER, customer)
                put(COL_INVOICE_TOTAL, total)
                put(COL_INVOICE_CREATED, now)
            })
            insertItems(database, invoiceId, items)
            database.insert(TABLE_REPORTS, null, ContentValues().apply {
                put(COL_REPORT_INVOICE_ID, invoiceId)
                put(COL_REPORT_HEADER, "")
                put(COL_REPORT_CUSTOMER, customer)
                put(COL_REPORT_TOTAL, total)
                put(COL_REPORT_CREATED, now)
            })
            database.setTransactionSuccessful()
            return invoiceId
        } finally {
            database.endTransaction()
        }
    }

    /** تعديل فاتورة موجودة: العميل والأصناف والإجمالي، ويُحدَّث تقريرها أيضًا. */
    fun updateInvoice(id: Long, customer: String, items: List<InvoiceItem>) {
        val database = getDatabase()
        val total = items.sumOf { it.subtotal }
        val idArg = arrayOf(id.toString())
        database.beginTransaction()
        try {
            database.update(TABLE_INVOICES, ContentValues().apply {
                put(COL_INVOICE_CUSTOMER, customer)
                put(COL_INVOICE_TOTAL, total)
            }, "$COL_INVOICE_ID = ?", idArg)
            database.delete(TABLE_INVOICE_ITEMS, "$COL_ITEM_INVOICE_ID = ?", idArg)
            insertItems(database, id, items)
            val reportValues = ContentValues().apply {
                put(COL_REPORT_CUSTOMER, customer)
                put(COL_REPORT_TOTAL, total)
            }
            val updated = database.update(TABLE_REPORTS, reportValues, "$COL_REPORT_INVOICE_ID = ?", idArg)
            if (updated == 0) {
                database.insert(TABLE_REPORTS, null, ContentValues().apply {
                    put(COL_REPORT_INVOICE_ID, id)
                    put(COL_REPORT_HEADER, "")
                    put(COL_REPORT_CUSTOMER, customer)
                    put(COL_REPORT_TOTAL, total)
                    put(COL_REPORT_CREATED, System.currentTimeMillis())
                })
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun getInvoice(id: Long): Invoice? {
        val sql = "SELECT i.$COL_INVOICE_ID, i.$COL_INVOICE_CUSTOMER, i.$COL_INVOICE_TOTAL, " +
            "i.$COL_INVOICE_CREATED, " +
            "(SELECT COUNT(*) FROM $TABLE_INVOICE_ITEMS it WHERE it.$COL_ITEM_INVOICE_ID = i.$COL_INVOICE_ID) " +
            "FROM $TABLE_INVOICES i WHERE i.$COL_INVOICE_ID = ?"
        getDatabase().rawQuery(sql, arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) {
                Invoice(c.getLong(0), c.getString(1), c.getDouble(2), c.getLong(3), c.getInt(4))
            } else null
        }
    }

    fun getInvoices(): List<Invoice> {
        val list = mutableListOf<Invoice>()
        val sql = "SELECT i.$COL_INVOICE_ID, i.$COL_INVOICE_CUSTOMER, i.$COL_INVOICE_TOTAL, " +
            "i.$COL_INVOICE_CREATED, " +
            "(SELECT COUNT(*) FROM $TABLE_INVOICE_ITEMS it WHERE it.$COL_ITEM_INVOICE_ID = i.$COL_INVOICE_ID) " +
            "FROM $TABLE_INVOICES i ORDER BY i.$COL_INVOICE_CREATED DESC"
        getDatabase().rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                list.add(Invoice(c.getLong(0), c.getString(1), c.getDouble(2), c.getLong(3), c.getInt(4)))
            }
        }
        return list
    }

    fun getInvoiceItems(invoiceId: Long): List<InvoiceItem> {
        val list = mutableListOf<InvoiceItem>()
        getDatabase().query(
            TABLE_INVOICE_ITEMS, null, "$COL_ITEM_INVOICE_ID = ?", arrayOf(invoiceId.toString()),
            null, null, "$COL_ITEM_ID ASC"
        ).use { c ->
            val iProd = c.getColumnIndexOrThrow(COL_ITEM_PRODUCT_ID)
            val iName = c.getColumnIndexOrThrow(COL_ITEM_PRODUCT_NAME)
            val iPrice = c.getColumnIndexOrThrow(COL_ITEM_UNIT_PRICE)
            val iQty = c.getColumnIndexOrThrow(COL_ITEM_QUANTITY)
            while (c.moveToNext()) {
                list.add(InvoiceItem(c.getLong(iProd), c.getString(iName), c.getDouble(iPrice), c.getInt(iQty)))
            }
        }
        return list
    }

    fun deleteInvoice(id: Long) {
        val database = getDatabase()
        database.beginTransaction()
        try {
            database.delete(TABLE_INVOICE_ITEMS, "$COL_ITEM_INVOICE_ID = ?", arrayOf(id.toString()))
            database.delete(TABLE_REPORTS, "$COL_REPORT_INVOICE_ID = ?", arrayOf(id.toString()))
            database.delete(TABLE_INVOICES, "$COL_INVOICE_ID = ?", arrayOf(id.toString()))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    // ---------------- Reports ----------------

    fun insertReport(invoiceId: Long, header: String, customer: String, total: Double): Long {
        val values = ContentValues().apply {
            put(COL_REPORT_INVOICE_ID, invoiceId)
            put(COL_REPORT_HEADER, header)
            put(COL_REPORT_CUSTOMER, customer)
            put(COL_REPORT_TOTAL, total)
            put(COL_REPORT_CREATED, System.currentTimeMillis())
        }
        return getDatabase().insert(TABLE_REPORTS, null, values)
    }

    fun getReports(): List<Report> {
        val list = mutableListOf<Report>()
        getDatabase().query(TABLE_REPORTS, null, null, null, null, null, "$COL_REPORT_CREATED DESC").use { c ->
            val iId = c.getColumnIndexOrThrow(COL_REPORT_ID)
            val iInv = c.getColumnIndexOrThrow(COL_REPORT_INVOICE_ID)
            val iHeader = c.getColumnIndexOrThrow(COL_REPORT_HEADER)
            val iCust = c.getColumnIndexOrThrow(COL_REPORT_CUSTOMER)
            val iTotal = c.getColumnIndexOrThrow(COL_REPORT_TOTAL)
            val iCreated = c.getColumnIndexOrThrow(COL_REPORT_CREATED)
            while (c.moveToNext()) {
                list.add(
                    Report(
                        c.getLong(iId), c.getLong(iInv), c.getString(iHeader) ?: "",
                        c.getString(iCust), c.getDouble(iTotal), c.getLong(iCreated)
                    )
                )
            }
        }
        return list
    }

    fun deleteReport(id: Long) {
        getDatabase().delete(TABLE_REPORTS, "$COL_REPORT_ID = ?", arrayOf(id.toString()))
    }
}
