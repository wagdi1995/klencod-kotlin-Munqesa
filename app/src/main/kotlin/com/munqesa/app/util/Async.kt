package com.munqesa.app.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** تشغيل الأعمال الثقيلة (قاعدة البيانات، PDF، الاستيراد) خارج الخيط الرئيسي. */
object Async {
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun <T> background(
        work: () -> T,
        onDone: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        io.execute {
            try {
                val result = work()
                main.post { onDone(result) }
            } catch (t: Throwable) {
                main.post { onError?.invoke(t) }
            }
        }
    }
}
