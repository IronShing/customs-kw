package com.ironshing.customs

import android.content.Context

/** Fetches every declaration for the saved commercial registration numbers and merges it into the store. */
object Checker {
    enum class Kind { NEW, CHANGED, RELEASED }
    data class Change(val dec: Dec, val kind: Kind)
    data class Outcome(val decs: List<Dec>, val changes: List<Change>, val error: String?)

    private val lock = Any()

    fun run(c: Context): Outcome = synchronized(lock) {
        val old = Store.load(c)
        val found = linkedMapOf<String, Pair<Epa.Row, String>>()
        val en = mutableMapOf<String, Epa.Row>()
        val ar = Epa.Session()
        val eng = Epa.Session(english = true)
        var error: String? = null
        for (cr in Store.crs(c)) {
            try {
                ar.search(cr = cr).forEach { found[it.number] = it to cr }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                continue
            }
            runCatching { eng.search(cr = cr) }.getOrNull()?.forEach { en[it.number] = it }
        }
        // A pending declaration the commercial-registration list no longer shows is looked up by its own number.
        if (error == null) {
            for (d in old) {
                if (d.released || d.row.number in found) continue
                runCatching { ar.search(dec = d.row.number) }.getOrNull()
                    ?.firstOrNull { it.number == d.row.number }?.let { found[it.number] = it to d.cr }
                runCatching { eng.search(dec = d.row.number) }.getOrNull()
                    ?.firstOrNull { it.number == d.row.number }?.let { en[it.number] = it }
            }
        }
        val now = System.currentTimeMillis()
        val (decs, changes) = merge(old, found.values.toList(), en, now)
        Store.save(c, decs)
        Store.setResult(c, now, error)
        Outcome(decs, changes, error)
    }

    fun merge(old: List<Dec>, found: List<Pair<Epa.Row, String>>, en: Map<String, Epa.Row>, now: Long): Pair<List<Dec>, List<Change>> {
        val byNumber = old.associateByTo(linkedMapOf()) { it.row.number }
        val changes = mutableListOf<Change>()
        for ((row, cr) in found) {
            val prev = byNumber[row.number]
            val e = en[row.number]
            val same = prev != null && prev.row.status == row.status && prev.row.lab == row.lab
            val released = Store.isReleased(row.status)
            val dec = Dec(
                row = row, cr = cr,
                statusEn = e?.status ?: prev?.statusEn?.takeIf { same },
                sectionEn = e?.section ?: prev?.sectionEn,
                labEn = e?.lab ?: prev?.labEn?.takeIf { same },
                firstSeen = prev?.firstSeen ?: now,
                checked = now,
                releasedAt = if (released) prev?.releasedAt?.takeIf { it > 0 } ?: now else 0,
            )
            byNumber[row.number] = dec
            when {
                prev == null -> changes += Change(dec, Kind.NEW)
                released && !prev.released -> changes += Change(dec, Kind.RELEASED)
                !same -> changes += Change(dec, Kind.CHANGED)
            }
        }
        return byNumber.values.toList() to changes
    }
}
