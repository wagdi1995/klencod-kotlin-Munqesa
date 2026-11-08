package com.munqesa.app.ui

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.munqesa.app.R
import com.munqesa.app.data.DatabaseHelper
import com.munqesa.app.data.Product
import com.munqesa.app.util.Async
import com.munqesa.app.util.ExcelImporter
import com.munqesa.app.util.Format
import com.munqesa.app.util.Press
import com.munqesa.app.util.showShortToast
import java.io.FileNotFoundException

class ProductsFragment : Fragment(R.layout.fragment_products) {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: ProductAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var countText: TextView
    private var query = ""
    private var loadToken = 0
    private val handler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { load() }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFile(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DatabaseHelper.get(requireContext())

        recycler = view.findViewById(R.id.recycler)
        emptyView = view.findViewById(R.id.emptyView)
        countText = view.findViewById(R.id.countText)

        adapter = ProductAdapter(
            onEdit = { showProductDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // تأخير بسيط حتى لا نستعلم عن قاعدة البيانات مع كل حرف يكتبه المستخدم
        view.findViewById<TextInputEditText>(R.id.searchInput).doOnTextChanged { text, _, _, _ ->
            query = text?.toString().orEmpty().trim()
            handler.removeCallbacks(searchRunnable)
            handler.postDelayed(searchRunnable, 180)
        }
        val addFab = view.findViewById<View>(R.id.addFab)
        val importButton = view.findViewById<View>(R.id.importButton)
        val deleteAllButton = view.findViewById<View>(R.id.deleteAllButton)
        Press.attach(addFab, 0.9f)
        Press.attach(importButton)
        Press.attach(deleteAllButton)
        addFab.setOnClickListener { showProductDialog(null) }
        importButton.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }

        load()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(searchRunnable)
        super.onDestroyView()
    }

    /** يقرأ الأصناف في الخلفية ويحدّث القائمة؛ النتيجة الأقدم تُهمَل إن وصلت متأخرة. */
    private fun load(afterCommit: (() -> Unit)? = null) {
        val token = ++loadToken
        val q = query
        Async.background({ db.getProducts(q) }, { products ->
            if (token != loadToken || view == null) return@background
            adapter.submit(products, afterCommit)
            emptyView.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
            countText.text = if (products.isEmpty()) "" else
                getString(R.string.products_count, products.size.toString())
        })
    }

    private fun showProductDialog(product: Product?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_product, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.nameLayout)
        val priceLayout = view.findViewById<TextInputLayout>(R.id.priceLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.productNameInput)
        val priceInput = view.findViewById<TextInputEditText>(R.id.productPriceInput)
        if (product != null) {
            nameInput.setText(product.name)
            priceInput.setText(Format.plain(product.price))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (product == null) R.string.products_add_manual else R.string.products_edit_title)
            .setView(view)
            .setPositiveButton(R.string.products_save, null)
            .setNegativeButton(R.string.products_cancel, null)
            .create()

        // نمنع الإغلاق التلقائي حتى نتحقق من المدخلات
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val price = Format.parsePrice(priceInput.text.toString())
                nameLayout.error = null
                priceLayout.error = null
                if (name.isEmpty()) {
                    nameLayout.error = getString(R.string.error_name_required)
                    return@setOnClickListener
                }
                if (price == null || price <= 0) {
                    priceLayout.error = getString(R.string.error_price_invalid)
                    return@setOnClickListener
                }
                if (product == null) {
                    db.insertProduct(name, price)
                    load { if (adapter.itemCount > 0) recycler.scrollToPosition(adapter.itemCount - 1) }
                    requireContext().showShortToast(getString(R.string.product_added))
                } else {
                    db.updateProduct(product.id, name, price)
                    load()
                    requireContext().showShortToast(getString(R.string.product_updated))
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_product)
            .setMessage(product.name)
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteProduct(product.id)
                load()
                requireContext().showShortToast(getString(R.string.product_deleted))
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_all)
            .setMessage(R.string.confirm_delete_all_msg)
            .setPositiveButton(R.string.yes) { _, _ ->
                db.deleteAllProducts()
                load()
                requireContext().showShortToast(getString(R.string.products_deleted_all))
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun importFile(uri: Uri) {
        val ctx = requireContext()
        Async.background(
            work = {
                val stream = ctx.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException()
                val products = ExcelImporter.importProducts(stream)
                if (products.isNotEmpty()) db.insertProducts(products.map { it.name to it.price })
                products.size
            },
            onDone = { count ->
                if (count == 0) {
                    ctx.showShortToast(getString(R.string.import_empty))
                } else {
                    if (view != null) load()
                    ctx.showShortToast(getString(R.string.import_success_count, count.toString()))
                }
            },
            onError = { e ->
                ctx.showShortToast(
                    when (e) {
                        is FileNotFoundException -> getString(R.string.file_not_found)
                        is IllegalArgumentException -> getString(R.string.excel_format_error)
                        else -> getString(R.string.import_failed)
                    }
                )
            }
        )
    }
}
