package com.ironshing.customs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A declaration as last seen, plus the site's English wording when we could fetch it. */
data class Dec(
    val row: Epa.Row,
    val cr: String,
    val statusEn: String? = null,
    val sectionEn: String? = null,
    val labEn: String? = null,
    val firstSeen: Long = 0,
    val checked: Long = 0,
    val releasedAt: Long = 0,
) {
    val released get() = Store.isReleased(row.status)
}

object Store {
    private const val PREFS = "customs"
    private val RELEASED = Tr.key("تم الإفراج - الجمارك")

    /** "تم الإفراج - الجمارك" is the only done state; everything else is pending. */
    fun isReleased(status: String) = Tr.key(status).contains(RELEASED)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun crs(c: Context): List<String> =
        (prefs(c).getString("crs", "") ?: "").split(Regex("[,\\s]+")).filter { it.isNotBlank() }

    fun setCrs(c: Context, v: String) = prefs(c).edit().putString("crs", v.trim()).apply()

    fun english(c: Context) = prefs(c).getBoolean("english", true)
    fun setEnglish(c: Context, v: Boolean) = prefs(c).edit().putBoolean("english", v).apply()

    fun background(c: Context) = prefs(c).getBoolean("background", true)
    fun setBackground(c: Context, v: Boolean) = prefs(c).edit().putBoolean("background", v).apply()

    fun lastCheck(c: Context) = prefs(c).getLong("lastCheck", 0)
    fun lastError(c: Context) = prefs(c).getString("lastError", null)
    fun setResult(c: Context, at: Long, error: String?) =
        prefs(c).edit().putLong("lastCheck", at).putString("lastError", error).apply()

    fun load(c: Context): List<Dec> = decode(prefs(c).getString("decs", "[]") ?: "[]")
    fun save(c: Context, decs: Collection<Dec>) = prefs(c).edit().putString("decs", encode(decs)).apply()

    fun encode(decs: Collection<Dec>): String = JSONArray().apply {
        decs.forEach { d ->
            val r = d.row
            put(JSONObject().apply {
                put("id", r.id); put("number", r.number); put("date", r.date); put("company", r.company)
                put("port", r.port); put("status", r.status); put("payment", r.payment); put("invoices", r.invoices)
                put("cost", r.cost); put("section", r.section); put("lab", r.lab); put("receipt", r.receipt ?: "")
                put("cr", d.cr); put("statusEn", d.statusEn ?: ""); put("sectionEn", d.sectionEn ?: ""); put("labEn", d.labEn ?: "")
                put("firstSeen", d.firstSeen); put("checked", d.checked); put("releasedAt", d.releasedAt)
            })
        }
    }.toString()

    fun decode(json: String): List<Dec> {
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            fun s(k: String) = o.optString(k, "")
            Dec(
                Epa.Row(s("id"), s("number"), s("date"), s("company"), s("port"), s("status"), s("payment"),
                    s("invoices"), s("cost"), s("section"), s("lab"), s("receipt").ifEmpty { null }),
                s("cr"), s("statusEn").ifEmpty { null }, s("sectionEn").ifEmpty { null }, s("labEn").ifEmpty { null },
                o.optLong("firstSeen"), o.optLong("checked"), o.optLong("releasedAt"),
            )
        }
    }
}
