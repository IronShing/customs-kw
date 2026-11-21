package com.ironshing.customs

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the EPA customs-declaration search (no login). Since 2026-11 it is an ASP.NET MVC page: GET the page for its
 * anti-forgery token and cookie, then POST the search, which answers JSON {"success", "data": [declarations]}.
 * English is chosen by POSTing Home/Culture, which sets the session cookie on its redirect.
 */
object Epa {
    private const val BASE = "https://customspayment.epa.gov.kw/CustomsDec"
    const val PAGE = "$BASE/declarations"
    private const val UA = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"

    data class Row(
        val id: String, val number: String, val date: String, val company: String, val port: String,
        val status: String, val payment: String, val invoices: String, val cost: String,
        val section: String, val lab: String, val receipt: String?,
    )

    /** One cookie session; [english] switches the site language once before searching. */
    class Session(private val english: Boolean = false) {
        private val cookies = linkedMapOf<String, String>()
        private var page: String? = null

        fun search(cr: String = "", dec: String = ""): List<Row> {
            val token = token(start(), "search-form") ?: throw IOException("Unexpected page from customspayment.epa.gov.kw")
            val json = http("$BASE/Declarations/Search", mapOf("__RequestVerificationToken" to token, "DeclarationNumber" to dec, "CommercialNumber" to cr))
            val o = runCatching { JSONObject(json) }.getOrNull() ?: throw IOException("Unexpected answer from customspayment.epa.gov.kw")
            if (!o.optBoolean("success")) {
                page = null // a stale token: fetch the page again next time
                throw IOException(o.optString("message").ifBlank { "The site refused the search" })
            }
            return parse(o.optJSONArray("data") ?: JSONArray(), english)
        }

        private fun start(): String {
            page?.let { return it }
            var html = http(PAGE, null)
            if (english) {
                token(html, "Home/Culture")?.let { t ->
                    http("$BASE/Home/Culture", mapOf("__RequestVerificationToken" to t, "language" to "en", "returnUrl" to "/CustomsDec/declarations"))
                    html = http(PAGE, null)
                }
            }
            return html.also { page = it }
        }

        private fun http(url: String, form: Map<String, String>?): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false // keep the cookies a redirect sets
            c.setRequestProperty("User-Agent", UA)
            if (cookies.isNotEmpty()) c.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                c.setRequestProperty("Referer", PAGE)
                val body = form.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            try {
                val code = c.responseCode
                c.headerFields.filterKeys { it.equals("Set-Cookie", true) }.values.flatten().forEach {
                    val kv = it.substringBefore(';')
                    cookies[kv.substringBefore('=').trim()] = kv.substringAfter('=', "")
                }
                if (code in 300..399) return ""
                // The search answers its refusals (expired token, bad input) as JSON with HTTP 400.
                val stream = if (code == 200) c.inputStream else if (code == 400 && form != null) c.errorStream else throw IOException("HTTP $code")
                return stream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
    }

    /** The anti-forgery token of the form whose tag contains [form] (its id or action). */
    fun token(html: String, form: String): String? =
        Regex("""<form\b[^>]*${Regex.escape(form)}[^>]*>\s*<input name="__RequestVerificationToken" type="hidden" value="([^"]*)"""")
            .find(html)?.groupValues?.get(1)

    fun parse(a: JSONArray, english: Boolean): List<Row> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        fun s(k: String) = o.optString(k).takeIf { it != "null" }.orEmpty().replace(Regex("\\s+"), " ").trim()
        val paid = o.optBoolean("Paid")
        Row(
            id = s("Id"), number = s("Number"), date = s("Date"), company = s("Importer"), port = s("Port"), status = s("Status"),
            payment = if (english) (if (paid) "Paid" else "Unpaid") else (if (paid) "مدفوع" else "غير مدفوع"),
            invoices = s("InvoiceCount"), cost = "%.3f".format(java.util.Locale.ROOT, o.optDouble("Fee", 0.0)),
            section = s("Department"), lab = s("QcStatus"), receipt = s("PaymentInfoUrl").ifBlank { null },
        )
    }
}
