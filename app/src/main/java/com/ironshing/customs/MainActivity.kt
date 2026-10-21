package com.ironshing.customs

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var decs by mutableStateOf<List<Dec>>(emptyList())
    private var busy by mutableStateOf(false)
    private var lastCheck by mutableStateOf(0L)
    private var error by mutableStateOf<String?>(null)
    private var english by mutableStateOf(true)
    private var crs by mutableStateOf("")
    private var background by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        english = Store.english(this)
        reload()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        CheckJob.schedule(this)
        setContent {
            AppTheme {
                Main(
                    decs = decs, busy = busy, lastCheck = lastCheck, error = error, english = english,
                    crs = crs, background = background,
                    parcels = packageManager.getLaunchIntentForPackage(PARCELS) != null,
                    onRefresh = ::refresh,
                    onLanguage = { english = !english; Store.setEnglish(this, english) },
                    onParcels = { packageManager.getLaunchIntentForPackage(PARCELS)?.let(::startActivity) },
                    onReceipt = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                    onSite = { startActivity(Intent(this, SiteActivity::class.java)) },
                    onSave = { crs, bg ->
                        val changed = crs.trim() != Store.crs(this).joinToString(", ")
                        Store.setCrs(this, crs)
                        Store.setBackground(this, bg)
                        CheckJob.schedule(this)
                        reload()
                        if (changed) refresh()
                    },
                )
            }
        }
    }

    // Opening the app is the trigger: check again unless we just did.
    override fun onStart() {
        super.onStart()
        reload()
        if (crs.isNotEmpty() && System.currentTimeMillis() - lastCheck > 30_000) refresh()
    }

    private fun reload() {
        decs = Store.load(this)
        lastCheck = Store.lastCheck(this)
        error = Store.lastError(this)
        crs = Store.crs(this).joinToString(", ")
        background = Store.background(this)
    }

    private fun refresh() {
        if (busy || crs.isEmpty()) return
        busy = true
        Thread {
            val out = runCatching { Checker.run(this) }
            runOnUiThread {
                out.onSuccess { decs = it.decs; error = it.error }.onFailure { error = it.message }
                lastCheck = Store.lastCheck(this)
                busy = false
            }
        }.start()
    }

    companion object {
        const val PARCELS = "com.brightstripe.parcels"
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun dateKey(d: String) = d.split('/').let { if (it.size == 3) it[2] + it[1] + it[0] else d }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(
    decs: List<Dec>, busy: Boolean, lastCheck: Long, error: String?, english: Boolean,
    crs: String, background: Boolean, parcels: Boolean,
    onRefresh: () -> Unit, onLanguage: () -> Unit, onParcels: () -> Unit,
    onReceipt: (String) -> Unit, onSite: () -> Unit, onSave: (String, Boolean) -> Unit,
) {
    fun t(en: String, ar: String) = if (english) en else ar
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    val sorted = decs.sortedWith(compareByDescending<Dec> { dateKey(it.row.date) }.thenByDescending { it.row.id.toLongOrNull() ?: 0 })
    val pending = sorted.filter { !it.released }
    val archive = sorted.filter { it.released }
    val dir = if (english) LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(t("Customs declarations", "البيانات الجمركية"))
                        val sub = when {
                            busy -> t("Checking…", "جارٍ التحقق…")
                            error != null -> t("Check failed: ", "فشل التحقق: ") + error
                            lastCheck > 0 -> t("CR $crs · checked ", "السجل $crs · ") +
                                SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH).format(Date(lastCheck))
                            else -> t("CR $crs", "السجل $crs")
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = if (error != null && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (parcels) IconButton(onClick = onParcels) { Icon(Icons.Outlined.Inventory2, "Parcels") }
                    IconButton(onClick = onLanguage) { Icon(Icons.Default.Translate, t("عربي", "English")) }
                    IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        }) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(t("Pending (${pending.size})", "قيد الانتظار (${pending.size})")) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(t("Released (${archive.size})", "تم الإفراج (${archive.size})")) })
                }
                PullToRefreshBox(isRefreshing = busy, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    val list = if (tab == 0) pending else archive
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (list.isEmpty()) item {
                            Text(
                                when {
                                    decs.isEmpty() && busy -> t("Loading declarations…", "جارٍ تحميل البيانات…")
                                    decs.isEmpty() -> t("No declarations yet. Pull to check.", "لا توجد بيانات. اسحب للتحقق.")
                                    tab == 0 -> t("Nothing pending — everything is released.", "لا يوجد بيان قيد الانتظار.")
                                    else -> t("Nothing released yet.", "لا يوجد بيان مُفرج عنه بعد.")
                                },
                                Modifier.fillMaxWidth().padding(top = 48.dp), textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(list, key = { it.row.number }) { DecCard(it, english, onReceipt) }
                        item {
                            TextButton(onClick = onSite, modifier = Modifier.fillMaxWidth()) {
                                Text(t("Open the EPA site", "فتح موقع الهيئة"))
                            }
                        }
                    }
                }
            }
        }
        // First launch: the importer CR has to be entered before anything can be checked.
        if (crs.isEmpty()) SettingsDialog(crs, background, english, required = true, onDismiss = {}) { c, b -> onSave(c, b) }
        else if (settings) SettingsDialog(crs, background, english, onDismiss = { settings = false }) { c, b ->
            settings = false
            onSave(c, b)
        }
    }
}

@Composable
private fun DecCard(d: Dec, english: Boolean, onReceipt: (String) -> Unit) {
    val r = d.row
    val green = if (isSystemInDarkTheme()) Color(0xFF7BD88F) else Color(0xFF1B7F3B)
    val amber = if (isSystemInDarkTheme()) Color(0xFFFFC66D) else Color(0xFF9A5B00)
    fun v(ar: String, official: String? = null) = if (english) Tr.en(ar, official) else ar
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.number, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(r.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = (if (d.released) green else amber).copy(alpha = 0.14f),
                contentColor = if (d.released) green else amber,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(v(r.status, d.statusEn), fontWeight = FontWeight.SemiBold)
                    if (english && Tr.hasArabic(r.status)) Text(r.status, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Line(if (english) "Port" else "المنفذ", v(r.port))
            Line(if (english) "Lab" else "المختبر", v(r.lab, d.labEn))
            Line(if (english) "Section" else "القسم", v(r.section))
            Line(if (english) "Company" else "الشركة", v(r.company))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (english) "${v(r.payment)} · KD ${r.cost} · ${r.invoices} invoice" + (if (r.invoices == "1") "" else "s")
                    else "${r.payment} · ${r.cost} د.ك · ${r.invoices} فاتورة"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                r.receipt?.let { url ->
                    TextButton(onClick = { onReceipt(url) }) {
                        Icon(Icons.Outlined.ReceiptLong, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (english) "Receipt" else "الإيصال")
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsDialog(
    crs: String, background: Boolean, english: Boolean, required: Boolean = false,
    onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit,
) {
    fun t(en: String, ar: String) = if (english) en else ar
    var c by remember { mutableStateOf(crs) }
    var b by remember { mutableStateOf(background) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (required) t("Importer CR number", "رقم السجل التجارى للمستورد") else t("Settings", "الإعدادات")) },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required),
        text = {
            Column {
                if (required) {
                    Text(t("Enter the importer's commercial registration number used on the EPA customs-declaration search.",
                        "أدخل رقم السجل التجارى للمستورد المستخدم في البحث عن البيانات الجمركية."))
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(c, { c = it.filter { ch -> ch.isDigit() || ch == ',' || ch == ' ' } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(t("Importer CR number(s)", "رقم السجل التجاري للمستورد")) },
                    supportingText = { Text(t("Separate several with commas", "افصل بين الأرقام بفاصلة")) }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Text(t("Check every 3 h and notify", "تحقق كل ٣ ساعات مع تنبيه")) }
                    Switch(b, { b = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(c, b) }, enabled = c.isNotBlank()) { Text(t("Save", "حفظ")) } },
        dismissButton = { if (!required) TextButton(onClick = onDismiss) { Text(t("Cancel", "إلغاء")) } },
    )
}
