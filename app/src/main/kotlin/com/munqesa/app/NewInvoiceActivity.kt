package com.munqesa.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.munqesa.app.data.DatabaseHelper
import com.munqesa.app.data.InvoiceItem
import com.munqesa.app.data.Product
import com.munqesa.app.ui.InvoiceItemAdapter
import com.munqesa.app.util.Async
import com.munqesa.app.util.Format
import com.munqesa.app.util.Press
import com.munqesa.app.util.showShortToast

class NewInvoiceActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: InvoiceItemAdapter
    private lateinit var totalText: TextView
    private lateinit var itemsEmpty: View
    private lateinit var productLayout: TextInputLayout
    private lateinit var quantityLayout: TextInputLayout
    private lateinit var customerLayout: TextInputLayout
    private lateinit var dropdown: MaterialAutoCompleteTextView
    private lateinit var quantityInput: TextInputEditText

    private val items = mutableListOf<InvoiceItem>()
    private var selected: Product? = null

    /** أكبر من 0 عند تعديل عرض سعر موجود، وإلا فهي فاتورة جديدة. */
    private var editId: Long = -1L

    companion object {
        const val EXTRA_EDIT_INVOICE_ID = "edit_invoice_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_invoice)
        db = DatabaseHelper.get(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        editId = intent.getLongExtra(EXTRA_EDIT_INVOICE_ID, -1L)
        if (editId > 0) toolbar.setTitle(R.string.invoice_edit_title)

        totalText = findViewById(R.id.totalText)
        itemsEmpty = findViewById(R.id.itemsEmpty)
        productLayout = findViewById(R.id.productLayout)
        quantityLayout = findViewById(R.id.quantityLayout)
        customerLayout = findViewById(R.id.customerLayout)
        dropdown = findViewById(R.id.productDropdown)
        quantityInput = findViewById(R.id.quantityInput)
        val customerInput = findViewById<TextInputEditText>(R.id.customerNameInput)

        adapter = InvoiceItemAdapter { position ->
            if (position in items.indices) {
                items.removeAt(position)
                refresh()
            }
        }
        val recycler = findViewById<RecyclerView>(R.id.itemsRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        Async.background({ db.getProducts() }, { products ->
            if (!isFinishing && !isDestroyed) {
                dropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, products))
            }
        })
        dropdown.doOnTextChanged { text, _, _, _ ->
            // أي تعديل يدوي على النص يُلغي الصنف المختار
            if (selected != null && text?.toString() != selected?.name) {
                selected = null
                productLayout.helperText = null
            }
        }
        dropdown.setOnItemClickListener { parent, _, position, _ ->
            val product = parent.getItemAtPosition(position) as Product
            selected = product
            productLayout.error = null
            productLayout.helperText = getString(R.string.invoice_unit_price_fmt, Format.money(product.price))
        }

        if (editId > 0) {
            Async.background({ Pair(db.getInvoice(editId), db.getInvoiceItems(editId)) }, { loaded ->
                if (isFinishing || isDestroyed) return@background
                val invoice = loaded.first
                if (invoice != null) {
                    customerInput.setText(invoice.customer)
                    items.clear()
                    items.addAll(loaded.second)
                    refresh()
                }
            })
        }

        val addItemButton = findViewById<View>(R.id.addItemButton)
        val saveButton = findViewById<View>(R.id.saveInvoiceButton)
        Press.attach(addItemButton)
        Press.attach(saveButton, 0.97f)
        addItemButton.setOnClickListener { addItem() }
        saveButton.setOnClickListener {
            val customer = customerInput.text.toString().trim()
            customerLayout.error = null
            if (customer.isEmpty()) {
                customerLayout.error = getString(R.string.invoice_customer_error)
                return@setOnClickListener
            }
            if (items.isEmpty()) {
                showShortToast(getString(R.string.invoice_empty_items))
                return@setOnClickListener
            }
            if (editId > 0) {
                db.updateInvoice(editId, customer, items.toList())
                showShortToast(getString(R.string.invoice_updated))
            } else {
                // يُنشأ التقرير (عرض السعر) تلقائيًا مع الفاتورة في نفس العملية
                db.saveInvoiceWithReport(customer, items.toList())
                showShortToast(getString(R.string.invoice_saved))
            }
            finish()
        }

        refresh()
    }

    private fun addItem() {
        productLayout.error = null
        quantityLayout.error = null
        val product = selected
        if (product == null) {
            productLayout.error = getString(R.string.invoice_select_product_hint)
            return
        }
        val quantity = Format.parseInt(quantityInput.text.toString()) ?: 0
        if (quantity <= 0) {
            quantityLayout.error = getString(R.string.invoice_quantity_error)
            return
        }
        val index = items.indexOfFirst { it.productId == product.id }
        if (index >= 0) {
            items[index] = items[index].copy(quantity = items[index].quantity + quantity)
        } else {
            items.add(InvoiceItem(product.id, product.name, product.price, quantity))
        }
        dropdown.setText("", false)
        quantityInput.text = null
        selected = null
        productLayout.helperText = null
        refresh()
    }

    private fun refresh() {
        adapter.submit(items.toList())
        itemsEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        totalText.text = Format.money(items.sumOf { it.subtotal })
    }
}
