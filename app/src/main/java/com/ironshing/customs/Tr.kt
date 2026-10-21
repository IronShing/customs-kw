package com.ironshing.customs

/**
 * Tiny offline Arabic→English translator for the customs page.
 * Order: whole phrase → the site's own English (if it is Latin) → " - " parts → words → transliteration.
 */
object Tr {
    private val PHRASES = mapOf(
        // Declaration status
        "تم الإفراج - الجمارك" to "Released – Customs",
        "تم الإفراج" to "Released",
        "تم الافراج من الجمارك" to "Released by Customs",
        "تمت الموافقة - السلامة الكيميائية -مبدئيًا" to "Approved – Chemical Safety (initial)",
        "تمت الموافقة" to "Approved",
        "تمت الموافقة مبدئيا" to "Approved (initial)",
        "مبدئيا" to "initial",
        "نهائيا" to "final",
        "قيد المراجعة" to "Under review",
        "قيد الفحص" to "Under inspection",
        "قيد الإجراء" to "In progress",
        "قيد المعالجة" to "Processing",
        "بانتظار الموافقة" to "Awaiting approval",
        "بانتظار نتائج المختبر" to "Awaiting lab results",
        "بانتظار الفحص" to "Awaiting inspection",
        "بانتظار سحب العينة" to "Awaiting sampling",
        "تم سحب العينة" to "Sample taken",
        "تم ارسال نتائج المختبر" to "Lab results sent",
        "تم إرسال العينة للمختبر" to "Sample sent to lab",
        "مطابق للمواصفات" to "Meets specifications",
        "غير مطابق للمواصفات" to "Fails specifications",
        "بيان كيميائي" to "Chemical declaration",
        "مرفوض" to "Rejected",
        "تم الرفض" to "Rejected",
        "ملغي" to "Cancelled",
        "تم الإلغاء" to "Cancelled",
        "جديد" to "New",
        "مسودة" to "Draft",
        "معلق" to "On hold",
        "إعادة تصدير" to "Re-export",
        "إتلاف" to "Destruction",
        // Payment
        "مدفوع" to "Paid",
        "غير مدفوع" to "Unpaid",
        "بانتظار الدفع" to "Awaiting payment",
        "معفى" to "Exempt",
        // Sections
        "قسم السلامة الكيميائية" to "Chemical Safety Section",
        "السلامة الكيميائية" to "Chemical Safety",
        "قسم المواد الكيميائية" to "Chemicals Section",
        "قسم الأوزون" to "Ozone Section",
        "قسم الاوزان" to "Weights Section",
        "الجمارك" to "Customs",
        "الهيئة العامة للبيئة" to "Environment Public Authority",
        // Ports
        "مطار الكويت الدولي" to "Kuwait International Airport",
        "الجمرك البري" to "Land Customs",
        "الجمرك الجوي" to "Air Customs",
        "ميناء الشويخ" to "Shuwaikh Port",
        "ميناء الشعيبة" to "Shuaiba Port",
        "ميناء الدوحة" to "Doha Port",
        "ميناء عبدالله" to "Abdullah Port",
        "المستودعات الجمركية العامة" to "Public Customs Warehouses",
        "منفذ العبدلي" to "Abdali Border",
        "منفذ النويصيب" to "Nuwaiseeb Border",
        "منفذ السالمي" to "Salmi Border",
        "الشحن الجوي" to "Air Cargo",
        "البريد السريع" to "Express Mail",
        "البريد" to "Post",
        "بري" to "Land",
        "بحري" to "Sea",
        "جوي" to "Air",
    )

    private val WORDS = mapOf(
        "شركة" to "Co.", "مؤسسة" to "Est.", "التجارية" to "Trading", "للتجارة" to "Trading",
        "العامة" to "General", "العام" to "General", "والمقاولات" to "& Contracting", "المقاولات" to "Contracting",
        "الكويت" to "Kuwait", "الكويتية" to "Kuwaiti", "الدولية" to "International", "الدولي" to "International",
        "المتحدة" to "United", "الوطنية" to "National", "الخليج" to "Gulf", "الشرق" to "East", "الأوسط" to "Middle",
        "ذمم" to "LLC", "ذ.م.م" to "LLC", "ش.م.ك" to "KSC",
        "بريمري" to "Primary", "اوتو" to "Auto", "للسيارات" to "Automotive", "السيارات" to "Cars",
        "قطع" to "Parts", "غيار" to "Spare", "الديوان" to "Diwan", "الاميري" to "Amiri",
        "تم" to "", "و" to "and", "من" to "from", "الى" to "to", "في" to "in",
        "بيان" to "declaration", "المختبر" to "lab", "نتائج" to "results", "ارسال" to "sent",
        "الموافقة" to "approval", "موافقة" to "approval", "الإفراج" to "release", "الدفع" to "payment",
        "انتظار" to "waiting", "بانتظار" to "awaiting", "مطابق" to "conforms", "غير" to "not",
    )

    private val LETTERS = mapOf(
        'ا' to "a", 'أ' to "a", 'إ' to "i", 'آ' to "aa", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j",
        'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "dh", 'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh",
        'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q",
        'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'ة' to "a", 'و' to "o", 'ي' to "i",
        'ى' to "a", 'ء' to "", 'ئ' to "e", 'ؤ' to "o",
    )

    /** Letters-only key: drops diacritics, tatweel, spaces and dashes; folds alef/yeh/teh-marbuta forms. */
    fun key(s: String) = s
        .replace(Regex("[\\u064B-\\u0652\\u0670\\u0640]"), "")
        .replace(Regex("[أإآ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[\\s\\-–—_]+"), "")

    private val phraseKeys = PHRASES.mapKeys { key(it.key) }
    private val wordKeys = WORDS.mapKeys { key(it.key) }

    fun hasArabic(s: String) = s.any { it in '؀'..'ۿ' }

    fun en(ar: String, official: String? = null): String {
        if (!hasArabic(ar)) return ar
        phraseKeys[key(ar)]?.let { return it }
        if (official != null && official.isNotBlank() && !hasArabic(official)) return official
        val parts = ar.split(Regex("\\s*-\\s*")).filter { it.isNotBlank() }
        return parts.joinToString(" – ") { part -> phraseKeys[key(part)] ?: words(part) }
    }

    private fun words(part: String): String {
        if (!hasArabic(part)) return part
        val ws = part.split(Regex("\\s+")).filter { it.isNotBlank() }
        val out = ws.map { w -> wordKeys[key(w)] ?: if (hasArabic(w)) translit(w) else w }.filter { it.isNotEmpty() }.toMutableList()
        // "شركة X" / "مؤسسة X" reads as "X Co." in English.
        if (out.size > 1 && (out[0] == "Co." || out[0] == "Est.")) out.add(out.removeAt(0))
        return out.joinToString(" ")
    }

    fun translit(w: String): String {
        val b = StringBuilder()
        w.forEachIndexed { i, ch ->
            b.append(when {
                i == 0 && ch == 'و' -> "w"
                i == 0 && ch == 'ي' -> "y"
                i == 0 && ch == 'ا' && w.startsWith("ال") && w.length > 3 -> "a"
                else -> LETTERS[ch] ?: if (ch in 'ً'..'ْ') "" else ch.toString()
            })
        }
        val s = b.toString().replace(Regex("^al(?=[a-z])"), "al-")
        return s.replaceFirstChar { it.uppercase() }
    }
}
