package com.ironshing.customs

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Scrapes the EPA customs-declaration search (an ASP.NET WebForms page, no login). */
object Epa {
    const val PAGE = "https://customspayment.epa.gov.kw/CustomsDec/BrokerSearchDeclaration.aspx"
    private const val UA = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36"
    private const val DEC = "ctl00\$ContentPlaceHolder1\$tbDecNo"
    private const val CR = "ctl00\$ContentPlaceHolder1\$tbCommericalNo"
    private const val SEARCH = "ctl00\$ContentPlaceHolder1\$btnSearch"
    private const val LANG = "ctl00\$lnkSwithLang"

    data class Row(
        val id: String, val number: String, val date: String, val company: String, val port: String,
        val status: String, val payment: String, val invoices: String, val cost: String,
        val section: String, val lab: String, val receipt: String?,
    )

    /** One cookie session; [english] flips the site language once before searching. */
    class Session(private val english: Boolean = false) {
        private val cookies = linkedMapOf<String, String>()
        private var page: String? = null

        fun search(cr: String = "", dec: String = ""): List<Row> {
            val form = hidden(start())
            form["__EVENTTARGET"] = SEARCH
            form["__EVENTARGUMENT"] = ""
            form[DEC] = dec
            form[CR] = cr
            val html = http(form)
            if (!html.contains("tbCommericalNo")) throw IOException("Unexpected page from customspayment.epa.gov.kw")
            page = html
            return parse(html)
        }

        private fun start(): String {
            page?.let { return it }
            var html = http(null)
            if (english) {
                val form = hidden(html)
                form["__EVENTTARGET"] = LANG
                form["__EVENTARGUMENT"] = ""
                html = http(form)
            }
            return html.also { page = it }
        }

        private fun http(form: Map<String, String>?): String {
            val c = URL(PAGE).openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", UA)
            if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val body = form.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            try {
                if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
                c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten().forEach {
                    val kv = it.substringBefore(';')
                    cookies[kv.substringBefore('=').trim()] = kv.substringAfter('=', "")
                }
                return c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun attr(tag: String, name: String) = Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    fun hidden(html: String): MutableMap<String, String> {
        val out = linkedMapOf<String, String>()
        Regex("""<input\b[^>]*type="hidden"[^>]*>""").findAll(html).forEach { m ->
            val name = attr(m.value, "name") ?: return@forEach
            out[unescape(name)] = unescape(attr(m.value, "value") ?: "")
        }
        return out
    }

    fun parse(html: String): List<Row> {
        val table = Regex("""<table\b[^>]*id="ContentPlaceHolder1_grdSearch".*?</table>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.value ?: return emptyList()
        return Regex("""<tr\b.*?</tr>""", RegexOption.DOT_MATCHES_ALL).findAll(table).mapNotNull { tr ->
            val c = Regex("""<td\b[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL).findAll(tr.value).map { text(it.groupValues[1]) }.toList()
            if (c.size < 12 || c[2].isEmpty()) return@mapNotNull null
            val id = Regex("""hdnDecId[^>]*value="(\d+)"""").find(tr.value)?.groupValues?.get(1) ?: ""
            val receipt = Regex("""<a\b[^>]*href="([^"]*Result\.aspx[^"]*)"""").find(tr.value)?.groupValues?.get(1)
                ?.let { unescape(it).replace("http://", "https://") }
            Row(id, c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], receipt)
        }.toList()
    }

    private fun text(cell: String) =
        unescape(cell.replace(Regex("<[^>]+>"), " ")).replace(Regex("[\\s\\u00a0]+"), " ").trim()

    fun unescape(s: String) = s
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace(Regex("&#[xX]([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
