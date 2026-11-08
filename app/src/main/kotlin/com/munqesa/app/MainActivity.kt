package com.munqesa.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.munqesa.app.ui.InvoicesFragment
import com.munqesa.app.ui.LiquidGlassBottomNav
import com.munqesa.app.ui.ProductsFragment
import com.munqesa.app.ui.ReportsFragment

class MainActivity : BaseActivity() {

    private val tabTags = arrayOf("tab_products", "tab_invoices", "tab_reports")
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<LiquidGlassBottomNav>(R.id.bottom_nav)
        nav.setItems(
            listOf(
                LiquidGlassBottomNav.NavItem(0, getString(R.string.nav_products), R.drawable.ic_inventory),
                LiquidGlassBottomNav.NavItem(1, getString(R.string.nav_invoices), R.drawable.ic_receipt),
                LiquidGlassBottomNav.NavItem(2, getString(R.string.nav_reports), R.drawable.ic_report)
            )
        )

        if (savedInstanceState == null) {
            current = 0
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container, createFragment(0), tabTags[0])
                .commit()
        } else {
            // النظام يستعيد الأقسام وحالة الإخفاء تلقائيًا؛ نحتاج فقط رقم القسم الحالي
            current = savedInstanceState.getInt(KEY_TAB, 0)
        }
        nav.setSelectedItem(current)
        nav.setOnItemSelectedListener { index -> switchTo(index) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, current)
    }

    private fun createFragment(index: Int): Fragment = when (index) {
        1 -> InvoicesFragment()
        2 -> ReportsFragment()
        else -> ProductsFragment()
    }

    /**
     * الأقسام لا تُعاد إنشاؤها عند كل تنقل: تُنشأ مرة واحدة (عند أول زيارة) ثم تُخفى وتُظهر،
     * مع انتقال انسيابي بأسلوب iOS 26 (انزلاق + تلاشي + تصغير خفيف).
     */
    private fun switchTo(index: Int) {
        if (index == current) return
        val fm = supportFragmentManager
        val forward = index > current
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        if (forward) {
            tx.setCustomAnimations(R.anim.ios_enter_forward, R.anim.ios_exit_forward)
        } else {
            tx.setCustomAnimations(R.anim.ios_enter_back, R.anim.ios_exit_back)
        }
        fm.findFragmentByTag(tabTags[current])?.let { tx.hide(it) }
        val target = fm.findFragmentByTag(tabTags[index])
        if (target == null) {
            tx.add(R.id.fragment_container, createFragment(index), tabTags[index])
        } else {
            tx.show(target)
        }
        tx.commit()
        current = index
    }

    companion object {
        private const val KEY_TAB = "current_tab"
    }
}