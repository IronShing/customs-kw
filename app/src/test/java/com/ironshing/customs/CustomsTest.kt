package com.ironshing.customs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomsTest {
    private fun page() = javaClass.classLoader!!.getResource("search_ar.html").readText()

    @Test fun parsesSavedSearch() {
        val rows = Epa.parse(page())
        assertEquals(12, rows.size)
        val r = rows[0]
        assertEquals("TIM/10001/KWI26", r.number)
        assertEquals("20/10/2026", r.date)
        assertEquals("500025", r.id)
        assertEquals("مطار الكويت الدولي", r.port)
        assertEquals("5", r.cost)
        assertEquals("https://customspayment.epa.gov.kw/Customs/Result.aspx?PaymentID=900000000000000013", r.receipt)
        assertFalse(Store.isReleased(r.status))
        assertTrue(rows.drop(1).all { Store.isReleased(it.status) })
    }

    @Test fun hiddenFields() {
        val f = Epa.hidden(page())
        assertTrue(f.getValue("__VIEWSTATE").length > 100)
        assertEquals("2D2F4CD8", f["__VIEWSTATEGENERATOR"])
    }

    @Test fun translates() {
        assertEquals("Released – Customs", Tr.en("تم الإفراج - الجمارك"))
        assertEquals("Approved – Chemical Safety (initial)", Tr.en("تمت الموافقة - السلامة الكيميائية -مبدئيًا"))
        assertEquals("LABCO – Lab results sent – Meets specifications", Tr.en("LABCO - تم ارسال نتائج المختبر - مطابق للمواصفات"))
        assertEquals("KCL – Chemical declaration – Approved", Tr.en("KCL - بيان كيميائي - تمت الموافقة"))
        assertEquals("Public Customs Warehouses – Land", Tr.en("المستودعات الجمركية العامة- بري"))
        assertEquals("Gulf Automotive Co.", Tr.en("شركة الخليج للسيارات"))
        assertEquals("Shuwaikh Port", Tr.en("ميناء الشويخ"))
        // Unknown status: the site's own English wins over word-by-word.
        assertEquals("Awaiting X", Tr.en("حالة مجهولة تماما", "Awaiting X"))
        assertEquals("Paid", Tr.en("مدفوع"))
    }

    @Test fun mergeArchivesReleased() {
        val rows = Epa.parse(page())
        val pending = rows[0]
        val (first, ch1) = Checker.merge(emptyList(), rows.map { it to "123456" }, emptyMap(), 1000)
        assertEquals(12, ch1.count { it.kind == Checker.Kind.NEW })
        assertEquals(1, first.count { !it.released })

        val done = pending.copy(status = "تم الإفراج - الجمارك")
        val (second, ch2) = Checker.merge(first, listOf(done to "123456"), emptyMap(), 2000)
        assertEquals(listOf(Checker.Kind.RELEASED), ch2.map { it.kind })
        assertEquals(12, second.size)
        assertTrue(second.all { it.released })
        assertEquals(2000, second.first { it.row.number == pending.number }.releasedAt)

        val (_, ch3) = Checker.merge(second, listOf(done to "123456"), emptyMap(), 3000)
        assertTrue(ch3.isEmpty())
    }
}
