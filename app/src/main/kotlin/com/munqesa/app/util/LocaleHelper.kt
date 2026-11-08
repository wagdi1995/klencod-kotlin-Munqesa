package com.munqesa.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** يجعل التطبيق عربيًا ومن اليمين لليسار بغض النظر عن لغة الهاتف. */
object LocaleHelper {
    fun wrap(base: Context): Context {
        val locale = Locale("ar")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
