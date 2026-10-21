# Customs

A small Android app that tracks Kuwait customs declarations on the Environment Public Authority's
[customs-declaration search](https://customspayment.epa.gov.kw/CustomsDec/BrokerSearchDeclaration.aspx)
by importer commercial registration number (رقم السجل التجارى للمستورد).

- On first launch you enter the importer CR number (several can be comma-separated).
- Every declaration (رقم البيان الجمركى) found for it is listed. Status **تم الإفراج - الجمارك** means
  released and goes to the *Released* tab; every other status stays *Pending*.
- Opening the app re-checks the site; a background job checks every 3 hours and notifies on new,
  changed or released declarations. Pending declarations that drop off the CR list are looked up by number.
- Arabic → English: a small built-in phrase table (`Tr.kt`), falling back to the site's own English
  wording, then a word glossary and transliteration. The 🌐 button switches the whole UI to Arabic.
- "Open the EPA site" opens the page in-app with the CR filled in and searched.

No login, account or API key is involved: the app posts the same search form the website uses.
Nothing leaves the phone except those searches.

Build: `./gradlew assembleRelease` (signing from `~/android/keys/signing.properties` if present).
Tests: `./gradlew testDebugUnitTest` (uses an anonymised saved results page).
