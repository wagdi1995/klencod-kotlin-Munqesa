package com.munqesa.app.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * يقرأ قائمة الأصناف من ملف xlsx أو csv.
 * العمود الأول = اسم الصنف، العمود الثاني = السعر. الصفوف غير الصالحة (مثل صف العناوين) تُتجاهل.
 * بدون أي مكتبة خارجية (لا حاجة لـ Apache POI).
 */
object ExcelImporter {

    data class ProductData(val name: String, val price: Double)

    /** @throws IllegalArgumentException إذا كان الملف بصيغة xls القديمة أو غير مدعوم */
    fun importProducts(input: InputStream): List<ProductData> {
        val bytes = input.use { it.readBytes() }
        val rows: List<List<String>> = when {
            bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() -> readXlsx(bytes)
            bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() ->
                throw IllegalArgumentException("xls")
            else -> readCsv(String(bytes, Charsets.UTF_8))
        }
        val result = mutableListOf<ProductData>()
        for (row in rows) {
            if (row.size < 2) continue
            val name = row[0].trim()
            val price = Format.parsePrice(row[1])
            if (name.isNotEmpty() && price != null && price > 0) result.add(ProductData(name, price))
        }
        return result
    }

    private fun readCsv(text: String): List<List<String>> =
        text.removePrefix("\uFEFF").lines()
            .filter { it.isNotBlank() }
            .map { line -> line.split(',', ';', '\t').map { it.trim().trim('"') } }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        var sharedXml: ByteArray? = null
        val sheets = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedXml = zip.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheets[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val sheet = sheets["xl/worksheets/sheet1.xml"]
            ?: sheets.toSortedMap().values.firstOrNull()
            ?: throw IllegalArgumentException("no sheet")
        val shared = sharedXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheet, shared)
    }

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), null)
        val list = mutableListOf<String>()
        var sb: StringBuilder? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "si" -> sb = StringBuilder()
                    "t" -> sb?.append(parser.nextText())
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "si") {
                sb?.let { list.add(it.toString()) }
                sb = null
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(data), null)
        val rows = mutableListOf<List<String>>()
        var current: java.util.TreeMap<Int, String>? = null
        var lastCol = -1
        var cellCol = 0
        var cellType: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "row" -> {
                        current = java.util.TreeMap()
                        lastCol = -1
                    }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        val ref = parser.getAttributeValue(null, "r")
                        cellCol = if (ref != null) columnIndex(ref) else lastCol + 1
                        lastCol = cellCol
                    }
                    "v" -> {
                        val value = parser.nextText()
                        val map = current
                        if (map != null) {
                            map[cellCol] = if (cellType == "s") {
                                shared.getOrElse(value.trim().toIntOrNull() ?: -1) { "" }
                            } else value
                        }
                    }
                    "t" -> {
                        val text = parser.nextText()
                        val map = current
                        if (map != null && cellType == "inlineStr") {
                            map[cellCol] = (map[cellCol] ?: "") + text
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "row") {
                val map = current
                if (map != null && map.isNotEmpty()) {
                    val maxCol = map.lastKey()
                    rows.add(List(maxCol + 1) { map[it] ?: "" })
                }
                current = null
            }
            event = parser.next()
        }
        return rows
    }

    /** "B7" -> 1 ، "AA3" -> 26 */
    private fun columnIndex(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') index = index * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') index = index * 26 + (ch - 'a' + 1)
            else break
        }
        return index - 1
    }
}
