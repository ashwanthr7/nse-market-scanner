package com.nse.marketscanner

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.CookieManager
import java.net.CookiePolicy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var symbolInput: EditText
    private lateinit var periodSpinner: Spinner
    private lateinit var thresholdSpinner: Spinner
    private lateinit var customDatesLayout: LinearLayout
    private lateinit var fromDateButton: MaterialButton
    private lateinit var toDateButton: MaterialButton
    private lateinit var analyzeButton: MaterialButton
    private lateinit var clearResultsButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var resultArea: LinearLayout
    private lateinit var savedContainer: LinearLayout
    private lateinit var marketContainer: LinearLayout
    private lateinit var savedSection1: LinearLayout
    private lateinit var savedSection2: LinearLayout
    private lateinit var savedInput1: EditText
    private lateinit var savedInput2: EditText
    private lateinit var tabLayout: TabLayout
    private lateinit var scroll: ScrollView
    private lateinit var marketPeriodSpinner: Spinner
    private lateinit var marketConditionChecks: MutableList<CheckBox>
    private lateinit var marketScanButton: MaterialButton
    private lateinit var marketProgressText: TextView
    private lateinit var marketResultsContainer: LinearLayout
    private val scannerPrefs: SharedPreferences by lazy { getSharedPreferences("market_scanner", MODE_PRIVATE) }

    private var fromDate: Calendar? = null
    private var toDate: Calendar? = null
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("ddMMyyyy", Locale.US)
    private val nseDateFormats = listOf(
        SimpleDateFormat("dd-MMM-yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )
    private val background = Executors.newSingleThreadExecutor()
    private val datePool = Executors.newFixedThreadPool(4)
    private val prefs: SharedPreferences by lazy { getSharedPreferences("delivery_ratio", MODE_PRIVATE) }
    private val savedSymbols1 = linkedSetOf<String>()
    private val savedSymbols2 = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onDestroy() {
        background.shutdownNow()
        datePool.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(12))
        }
        outer.addView(TextView(this).apply {
            text = "NSE Market Scanner"; textSize = 27f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(5), dp(4), 0)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        outer.addView(TextView(this).apply {
            text = "Fast native scanner • Delivery Ratio"
            textSize = 13.5f; setPadding(dp(4), 0, dp(4), dp(7))
        })
        scroll = ScrollView(this).apply { isFillViewport = true }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        marketContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(24))
        }
        buildMarketContent()
        scroll.addView(marketContainer)
        return outer
    }

    private fun buildAnalyzeContent() {
        resultArea.removeAllViews()
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); strokeWidth = dp(1)
            setContentPadding(dp(14), dp(12), dp(14), dp(14))
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        form.addView(label("Stock symbols"))
        symbolInput = EditText(this).apply {
            hint = "SKIPPER OLAELEC COCHINSHIP"
            textSize = 16f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(10), 0, dp(10), 0)
        }
        form.addView(symbolInput, LinearLayout.LayoutParams(-1, dp(52)))

        form.addView(label("Period").apply { setPadding(0, dp(10), 0, dp(4)) })
        val periods = listOf(
            "1 Month", "2 Months", "3 Months", "4 Months", "5 Months", "6 Months",
            "7 Months", "8 Months", "9 Months", "10 Months", "11 Months", "12 Months",
            "1 Year", "2 Years", "3 Years", "4 Years", "5 Years", "Custom Dates"
        )
        periodSpinner = Spinner(this)
        periodSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periods)
        form.addView(periodSpinner, LinearLayout.LayoutParams(-1, dp(48)))

        form.addView(label("Bullish threshold").apply { setPadding(0, dp(8), 0, dp(4)) })
        thresholdSpinner = Spinner(this)
        val thresholds = listOf("1.00×", "1.20×", "1.50×", "2.00×")
        thresholdSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, thresholds)
        form.addView(thresholdSpinner, LinearLayout.LayoutParams(-1, dp(48)))

        customDatesLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        fromDateButton = MaterialButton(this).apply { text = "From date"; isAllCaps = false; setOnClickListener { chooseDate(true) } }
        toDateButton = MaterialButton(this).apply { text = "To date"; isAllCaps = false; setOnClickListener { chooseDate(false) } }
        customDatesLayout.addView(fromDateButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4) })
        customDatesLayout.addView(toDateButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4) })
        form.addView(customDatesLayout)

        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                customDatesLayout.visibility = if (periods[position] == "Custom Dates") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        analyzeButton = MaterialButton(this).apply {
            text = "SCAN"
            textSize = 16f
            setOnClickListener { analyze() }
        }
        clearResultsButton = MaterialButton(this).apply {
            text = "Clear"
            isAllCaps = false
            setOnClickListener { clearResults() }
        }
        actionRow.addView(analyzeButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { rightMargin = dp(4); topMargin = dp(10) })
        actionRow.addView(clearResultsButton, LinearLayout.LayoutParams(dp(90), dp(54)).apply { leftMargin = dp(4); topMargin = dp(10) })
        form.addView(actionRow)

        progress = ProgressBar(this).apply { visibility = View.GONE; isIndeterminate = true }
        form.addView(progress, LinearLayout.LayoutParams(-1, dp(30)))
        progressText = TextView(this).apply { visibility = View.GONE; textSize = 12f; gravity = Gravity.CENTER }
        form.addView(progressText)
        card.addView(form)
        resultArea.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        resultArea.addView(TextView(this).apply {
            text = "Delivery Ratio = UP-day deliverable quantity ÷ DOWN-day deliverable quantity. 1.20× is the default bullish grouping."
            textSize = 12.5f
            setPadding(dp(6), dp(2), dp(6), dp(8))
        })
        resultArea.addView(TextView(this).apply {
            text = "No scan yet. Enter symbols and tap SCAN."
            textSize = 15f
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildSavedContent() {
        savedContainer.removeAllViews()
        savedContainer.addView(TextView(this).apply {
            text = "Saved Stocks"; textSize = 23f; setTypeface(null, android.graphics.Typeface.BOLD)
        })
        savedContainer.addView(TextView(this).apply {
            text = "Use two independent stock lists. Each list has its own Add, COPY ALL and Clear All."
            textSize = 13.5f; setPadding(0, dp(4), 0, dp(14))
        })

        savedSection1 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(14))
        }
        savedSection2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(14), dp(2), dp(24))
        }
        savedContainer.addView(savedSection1)
        savedContainer.addView(savedSection2)
        refreshSaved1()
        refreshSaved2()
    }

    private fun refreshSaved1() {
        if (!::savedSection1.isInitialized) return
        renderSavedSection(savedSection1, "Saved Stocks 1", savedSymbols1, 1)
    }

    private fun refreshSaved2() {
        if (!::savedSection2.isInitialized) return
        renderSavedSection(savedSection2, "Saved Stocks 2", savedSymbols2, 2)
    }

    private fun renderSavedSection(section: LinearLayout, title: String, symbols: LinkedHashSet<String>, slot: Int) {
        section.removeAllViews()
        section.addView(TextView(this).apply {
            text = title; textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply { hint = "Add symbol"; textSize = 16f; setSingleLine(true) }
        if (slot == 1) savedInput1 = input else savedInput2 = input
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
        inputRow.addView(MaterialButton(this).apply {
            text = "Add"; isAllCaps = false
            setOnClickListener {
                val entered = input.text.toString()
                    .uppercase(Locale.US)
                    .split(",", " ", "\n", "\t", ";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (entered.isNotEmpty()) {
                    symbols.addAll(entered)
                    saveSaved()
                    input.text.clear()
                    if (slot == 1) refreshSaved1() else refreshSaved2()
                    toast(if (entered.size == 1) "${entered.first()} added" else "${entered.size} stocks added separately")
                }
            }
        }, LinearLayout.LayoutParams(dp(86), dp(50)).apply { leftMargin = dp(6) })
        section.addView(inputRow)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(MaterialButton(this).apply {
            text = "COPY ALL"
            setOnClickListener { copySymbols(symbols.toList()) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(4); topMargin = dp(8) })
        actions.addView(MaterialButton(this).apply {
            text = "Clear All"; isAllCaps = false
            setOnClickListener {
                symbols.clear()
                saveSaved()
                if (slot == 1) refreshSaved1() else refreshSaved2()
            }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(4); topMargin = dp(8) })
        section.addView(actions)

        section.addView(TextView(this).apply {
            text = "Saved symbols"; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        })

        if (symbols.isEmpty()) {
            section.addView(TextView(this).apply {
                text = "No saved stocks yet."; textSize = 15f; setPadding(dp(8), dp(10), dp(8), dp(10))
            })
            return
        }

        symbols.forEach { symbol ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = symbol; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(MaterialButton(this).apply {
                text = "COPY"; textSize = 10f; minWidth = 0; minimumWidth = 0; setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener { copySymbol(symbol) }
            }, LinearLayout.LayoutParams(dp(72), dp(44)).apply { rightMargin = dp(3) })
            row.addView(MaterialButton(this).apply {
                text = "×"; textSize = 18f; minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
                setOnClickListener {
                    symbols.remove(symbol)
                    saveSaved()
                    if (slot == 1) refreshSaved1() else refreshSaved2()
                }
            }, LinearLayout.LayoutParams(dp(48), dp(44)))
            section.addView(row)
        }
    }

    private fun showTab(position: Int) {
        scroll.removeAllViews()
        when (position) {
            0 -> scroll.addView(resultArea)
            1 -> scroll.addView(savedContainer)
            else -> scroll.addView(marketContainer)
        }
    }

    private fun buildMarketContent() {
        marketContainer.removeAllViews()
        marketContainer.addView(TextView(this).apply {
            text = "Market Scanner"; textSize = 23f; setTypeface(null, android.graphics.Typeface.BOLD)
        })
        marketContainer.addView(TextView(this).apply {
            text = "NSE Delivery Ratio"
            textSize = 13f; setPadding(0, dp(4), 0, dp(12))
        })

        marketContainer.addView(label("Period").apply { setPadding(0, dp(4), 0, dp(4)) })
        val periods = listOf("1 Month","2 Months","3 Months","4 Months","5 Months","6 Months","7 Months","8 Months","9 Months","10 Months","11 Months","12 Months","1 Year","2 Years","3 Years","4 Years","5 Years")
        marketPeriodSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, periods); setSelection(5) }
        marketContainer.addView(marketPeriodSpinner, LinearLayout.LayoutParams(-1, dp(48)))

        marketContainer.addView(label("Delivery Ratio conditions").apply { setPadding(0, dp(10), 0, dp(4)) })
        marketConditionChecks = mutableListOf()
        listOf("> 2.00×", "> 1.50×", "> 1.20×", "1.20× – 1.50×", "< 0.80×").forEachIndexed { i, t ->
            marketConditionChecks.add(CheckBox(this).apply { text=t; textSize=15f; isChecked=i==2; setPadding(dp(4),0,0,0) }.also { marketContainer.addView(it, LinearLayout.LayoutParams(-1, dp(44))) })
        }

        marketScanButton=MaterialButton(this).apply { text="SCAN MARKET"; textSize=16f; setOnClickListener { scanFullMarket() } }
        marketContainer.addView(marketScanButton, LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin=dp(8) })
        marketProgressText=TextView(this).apply { textSize=12f; gravity=Gravity.CENTER; visibility=View.GONE }
        marketContainer.addView(marketProgressText, LinearLayout.LayoutParams(-1,dp(44)))
        marketResultsContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        marketContainer.addView(marketResultsContainer)
    }

    private fun scanFullMarket() {
        val conditions = selectedBuiltInConditions()
        if (conditions.isEmpty()) {
            toast("Select at least one Delivery Ratio condition.")
            return
        }
        val range = selectedMarketRange()
        marketScanButton.isEnabled = false
        marketProgressText.visibility = View.VISIBLE
        marketProgressText.text = "Loading NSE equity universe…"
        marketResultsContainer.removeAllViews()
        background.submit {
            try {
                val symbols = latestUniverse()
                if (symbols.isEmpty()) throw IllegalStateException("Unable to load NSE equity universe. Please try again.")
                runOnUiThread { marketProgressText.text = "Calculating Delivery Ratio for ${symbols.size} stocks…" }
                val results = calculateAll(symbols, range.first, range.second) { done, total ->
                    runOnUiThread { marketProgressText.text = "Fetching NSE data: $done / $total dates" }
                }
                runOnUiThread {
                    marketScanButton.isEnabled = true
                    marketProgressText.visibility = View.GONE
                    renderMarketResults(results, range.first, range.second, conditions, symbols.size)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    marketScanButton.isEnabled = true
                    marketProgressText.visibility = View.GONE
                    marketResultsContainer.removeAllViews()
                    marketResultsContainer.addView(TextView(this).apply {
                        text = "Scanner failed\n\n${e.message ?: "Unable to scan market"}"
                        textSize = 15f
                        setPadding(dp(8), dp(14), dp(8), dp(14))
                    })
                }
            }
        }
    }

    private fun selectedMarketRange(): Pair<Calendar, Calendar> {
        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
        val start = end.clone() as Calendar
        val selected = marketPeriodSpinner.selectedItem.toString()
        val n = selected.substringBefore(" ").toInt()
        if (selected.contains("Month")) start.add(Calendar.MONTH, -n) else start.add(Calendar.YEAR, -n)
        return start to end
    }

    private data class RatioCondition(val label: String, val test: (Double) -> Boolean)

    private fun selectedBuiltInConditions(): List<RatioCondition> {
        val selected = marketConditionChecks.withIndex().filter { it.value.isChecked }.map { it.index }
        return selected.mapNotNull { index ->
            when (index) {
                0 -> RatioCondition("> 2.00×") { it > 2.0 }
                1 -> RatioCondition("> 1.50×") { it > 1.5 }
                2 -> RatioCondition("> 1.20×") { it > 1.2 }
                3 -> RatioCondition("1.20× – 1.50×") { it >= 1.2 && it <= 1.5 }
                4 -> RatioCondition("< 0.80×") { it < 0.8 }
                else -> null
            }
        }
    }

    private fun latestUniverse(): List<String> {
        val urls = listOf(
            "https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv",
            "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
        )
        for (url in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
                    setRequestProperty("Accept", "text/csv,text/plain,*/*")
                    setRequestProperty("Referer", "https://www.nseindia.com/")
                    setRequestProperty("Connection", "close")
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) continue
                val universe = parseUniverse(ByteArrayInputStream(bytes))
                if (universe.isNotEmpty()) return universe
            } catch (_: Exception) {
                // Try the fallback host.
            } finally {
                conn?.disconnect()
            }
        }

        // Fallback: use the most recent available full bhavcopy if the current
        // securities-list file is temporarily unavailable.
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
        repeat(8) {
            val rows = fetchDayRows(emptyList(), c)
            if (rows.isNotEmpty()) return rows.keys.sorted()
            c.add(Calendar.DAY_OF_MONTH, -1)
        }
        return emptyList()
    }

    private fun parseUniverse(input: InputStream): List<String> {
        BufferedReader(InputStreamReader(input)).use { br ->
            val header = br.readLine() ?: return emptyList()
            val headers = parseCsv(header).mapIndexed { i, s -> normalize(s) to i }.toMap()
            val symbolIdx = headers["SYMBOL"] ?: return emptyList()
            val seriesIdx = headers["SERIES"]
            val out = linkedSetOf<String>()
            while (true) {
                val line = br.readLine() ?: break
                val cols = parseCsv(line)
                val maxIdx = maxOf(symbolIdx, seriesIdx ?: 0)
                if (cols.size <= maxIdx) continue
                if (seriesIdx != null && cols[seriesIdx].trim().uppercase(Locale.US) != "EQ") continue
                val symbol = cols[symbolIdx].trim().uppercase(Locale.US)
                if (symbol.isNotEmpty()) out.add(symbol)
            }
            return out.sorted()
        }
    }

    private fun renderMarketResults(results: List<StockResult>, from: Calendar, to: Calendar, conditions: List<RatioCondition>, universeSize: Int) {
        marketResultsContainer.removeAllViews()
        marketResultsContainer.addView(TextView(this).apply {
            text = "NSE Full Market • ${dateFormat.format(from.time)} → ${dateFormat.format(to.time)}\nStocks in universe: $universeSize • Stocks with data: ${results.size}"
            textSize = 13f; setPadding(dp(6), dp(2), dp(6), dp(10))
        })
        conditions.forEach { condition ->
            val matches = results.filter { condition.test(it.ratio) }.sortedByDescending { it.ratio }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "${condition.label}  (${matches.size})"; textSize=18f; setTypeface(null, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(0, dp(52), 1f))
            row.addView(MaterialButton(this).apply { text="COPY"; setOnClickListener { copySymbols(matches.map{it.symbol}) } }, LinearLayout.LayoutParams(dp(82),dp(44)))
            marketResultsContainer.addView(row)
            if (matches.isEmpty()) {
                marketResultsContainer.addView(TextView(this).apply { text="No matching stocks."; textSize=13f; setPadding(dp(8),0,dp(8),dp(8)) })
            } else {
                val lines = matches.joinToString("\n") { "${it.symbol}   ${if (it.ratio.isInfinite()) "∞" else fmt(it.ratio)+"×"}" }
                marketResultsContainer.addView(TextView(this).apply { text=lines; textSize=13.5f; setPadding(dp(8),0,dp(8),dp(12)) })
            }
        }
        marketResultsContainer.addView(MaterialButton(this).apply { text="COPY ALL"; setOnClickListener { copySymbols(conditions.flatMap { c -> results.filter { c.test(it.ratio) }.map { it.symbol } }.distinct()) } }, LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(8) })
        marketResultsContainer.addView(TextView(this).apply { text="Latest available NSE data is used for the selected range; each NSE daily file is fetched once and shared across all stocks."; textSize=11.5f; setPadding(dp(6),dp(12),dp(6),dp(4)) })
    }

    private fun chooseDate(isFrom: Boolean) {
        val base = (if (isFrom) fromDate else toDate) ?: Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val c = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            if (isFrom) { fromDate = c; fromDateButton.text = "From: ${dateFormat.format(c.time)}" }
            else { toDate = c; toDateButton.text = "To: ${dateFormat.format(c.time)}" }
            // A changed date range invalidates the previous calculation.
            // This prevents an old result (for example ending yesterday) from
            // being mistaken for the newly selected range until SCAN is pressed.
            clearResultViews()
            resultArea.addView(TextView(this).apply {
                text = "Date range changed. Tap SCAN to recalculate exactly for the selected dates."
                textSize = 13f
                setPadding(dp(8), dp(14), dp(8), dp(14))
            })
            saveLastSettings()
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun analyze() {
        val symbols = symbolInput.text.toString().uppercase(Locale.US)
            .split(",", " ", "\n", "\t", ";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (symbols.isEmpty()) { toast("Please enter at least one stock symbol."); return }
        val range = selectedRange() ?: return
        val threshold = thresholdSpinner.selectedItem.toString().replace("×", "").toDouble()
        saveLastSettings()
        analyzeButton.isEnabled = false
        progress.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = "Preparing NSE dates…"
        clearResultViews()
        background.submit {
            try {
                val results = calculateAll(symbols, range.first, range.second) { done, total ->
                    runOnUiThread { progressText.text = "Fetching NSE data: $done / $total dates" }
                }
                runOnUiThread {
                    analyzeButton.isEnabled = true; progress.visibility = View.GONE; progressText.visibility = View.GONE
                    renderResults(results, range.first, range.second, threshold)
                }
            } catch (e: Exception) {
                runOnUiThread { analyzeButton.isEnabled = true; progress.visibility = View.GONE; progressText.visibility = View.GONE; renderError(e.message ?: "Unable to analyze") }
            }
        }
    }

    private fun selectedRange(): Pair<Calendar, Calendar>? {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val selected = periodSpinner.selectedItem.toString()
        val start: Calendar
        val end: Calendar
        if (selected == "Custom Dates") {
            if (fromDate == null || toDate == null) { toast("Please select both From and To dates."); return null }
            start = (fromDate!!.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            end = (toDate!!.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        } else {
            end = today
            start = today.clone() as Calendar
            val n = selected.substringBefore(" ").toInt()
            if (selected.contains("Month")) start.add(Calendar.MONTH, -n) else start.add(Calendar.YEAR, -n)
        }
        if (start.after(end)) { toast("From date must be before To date."); return null }
        return start to end
    }

    private fun calculateAll(symbols: List<String>, from: Calendar, to: Calendar, onProgress: (Int, Int) -> Unit): List<StockResult> {
        val dates = mutableListOf<Calendar>()
        val d = from.clone() as Calendar
        while (!d.after(to)) {
            val dow = d.get(Calendar.DAY_OF_WEEK)
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) dates.add(d.clone() as Calendar)
            d.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (dates.isEmpty()) return symbols.map { StockResult(it, error = "No weekdays in selected period") }

        val totals = symbols.associateWith { MutableTotals(it) }.toMutableMap()
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val includesToday = !today.before(from) && !today.after(to) && today.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && today.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY

        fun applyRows(rows: Map<String, DayRow>) {
            rows.forEach { (symbol, row) ->
                val t = totals[symbol] ?: return@forEach
                t.days++
                when {
                    row.close > row.prevClose -> { t.up += row.delivery; t.upDays++ }
                    row.close < row.prevClose -> { t.down += row.delivery; t.downDays++ }
                    else -> { t.flat += row.delivery; t.flatDays++ }
                }
            }
        }

        // Fetch today's file separately first. This prevents NSE throttling from dropping the
        // newest day when a long period launches many archive requests at once.
        var completed = 0
        if (includesToday) {
            applyRows(fetchDayRows(symbols, today))
            completed++
            onProgress(completed, dates.size)
            dates.removeAll { sameCalendarDay(it, today) }
        }

        val futures = dates.map { date -> datePool.submit(Callable { fetchDayRows(symbols, date) }) }
        futures.forEach { future ->
            applyRows(future.get())
            completed++
            onProgress(completed, dates.size + if (includesToday) 1 else 0)
        }
        return symbols.map { symbol ->
            val t = totals[symbol]!!
            if (t.days == 0) StockResult(symbol, error = "Symbol not found or no NSE data")
            else {
                val ratio = if (t.down > 0) t.up.toDouble() / t.down else Double.POSITIVE_INFINITY
                StockResult(symbol, t.days, t.upDays, t.downDays, t.flatDays, t.up, t.down, t.flat, ratio)
            }
        }
    }

    private fun sameCalendarDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun fetchDayRows(symbols: List<String>, date: Calendar): Map<String, DayRow> {
        val wanted = symbols.map { it.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val ds = apiDateFormat.format(date.time)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val isToday = sameCalendarDay(date, today)
        val cacheDir = File(filesDir, "nse_cache").apply { if (!exists()) mkdirs() }
        val cacheFile = File(cacheDir, "$ds.csv.gz")

        // Historical dates: reuse the complete NSE file already downloaded.
        // This makes adding a new stock fast because no new network download is needed.
        if (!isToday && cacheFile.exists() && cacheFile.length() > 20L) {
            try {
                FileInputStream(cacheFile).use { input ->
                    GZIPInputStream(input).use { return parseDayRows(it, wanted, date) }
                }
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        val urls = listOf(
            "https://nsearchives.nseindia.com/products/content/sec_bhavdata_full_$ds.csv",
            "https://archives.nseindia.com/products/content/sec_bhavdata_full_$ds.csv"
        )
        repeat(3) { attempt ->
            for (url in urls) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 7000; readTimeout = 15000; requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
                        setRequestProperty("Accept", "text/csv,text/plain,*/*")
                        setRequestProperty("Referer", "https://www.nseindia.com/")
                        setRequestProperty("Connection", "close")
                    }
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) continue
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) continue

                    // Cache historical NSE files compressed. Today is always fetched fresh.
                    if (!isToday) {
                        try {
                            FileOutputStream(cacheFile).use { fos ->
                                GZIPOutputStream(fos).use { gz -> gz.write(bytes) }
                            }
                        } catch (_: Exception) { /* cache failure must not fail the scan */ }
                    }
                    return parseDayRows(ByteArrayInputStream(bytes), wanted, date)
                } catch (_: Exception) {
                    // Retry the same date against both NSE archive hosts.
                } finally { conn?.disconnect() }
            }
            if (attempt < 2) Thread.sleep(250L * (attempt + 1))
        }
        return emptyMap()
    }

    private fun parseDayRows(input: InputStream, wanted: Set<String>, date: Calendar): Map<String, DayRow> {
        BufferedReader(InputStreamReader(input)).use { br ->
            val header = br.readLine() ?: return emptyMap()
            val headers = parseCsv(header).mapIndexed { i, s -> normalize(s) to i }.toMap()
            val symbolIdx = headers["SYMBOL"] ?: return emptyMap()
            val seriesIdx = headers["SERIES"]
            val dateIdx = headers["DATE1"] ?: headers["DATE"] ?: return emptyMap()
            val prevIdx = headers["PRECLOSE"] ?: headers["PREVCLOSE"] ?: return emptyMap()
            val closeIdx = headers["CLOSEPRICE"] ?: return emptyMap()
            val delIdx = headers["DELIVQTY"] ?: headers["DELIVERABLEQTY"] ?: return emptyMap()
            val out = mutableMapOf<String, DayRow>()
            val maxIdx = maxOf(symbolIdx, seriesIdx ?: 0, dateIdx, prevIdx, closeIdx, delIdx)
            while (true) {
                val line = br.readLine() ?: break
                val cols = parseCsv(line)
                if (cols.size <= maxIdx) continue
                val symbol = cols[symbolIdx].trim().uppercase(Locale.US)
                if (wanted.isNotEmpty() && symbol !in wanted) continue
                if (seriesIdx != null && cols[seriesIdx].trim().uppercase(Locale.US) != "EQ") continue
                if (!sameNseDate(cols[dateIdx], date)) continue
                val prev = parseDouble(cols[prevIdx]); val close = parseDouble(cols[closeIdx]); val delivery = parseLong(cols[delIdx])
                if (prev != null && close != null && delivery != null) out[symbol] = DayRow(prev, close, delivery)
                if (wanted.isNotEmpty() && out.size == wanted.size) break
            }
            return out
        }
    }

    private fun renderResults(results: List<StockResult>, from: Calendar, to: Calendar, threshold: Double) {
        clearResultViews()
        resultArea.addView(TextView(this).apply {
            text = "${dateFormat.format(from.time)} → ${dateFormat.format(to.time)}   •   Bullish: ${String.format(Locale.US, "%.2f", threshold)}×"
            textSize = 13f; setPadding(dp(6), dp(2), dp(6), dp(8))
        })
        val valid = results.filter { it.days > 0 && it.error == null }
        val above = valid.filter { it.ratio >= threshold }.sortedByDescending { it.ratio }
        val below = valid.filter { it.ratio < threshold }.sortedByDescending { it.ratio }
        addSection("ABOVE ${fmt(threshold)}×", above, true)
        addSection("${fmt(threshold)}× & BELOW", below, false)
        val errors = results.filter { it.days == 0 || it.error != null }
        if (errors.isNotEmpty()) addErrorSection(errors)
        resultArea.addView(TextView(this).apply {
            text = "Note: Only NSE rows whose actual DATE falls inside the selected From → To range are counted. Changing a date clears the old result; tap SCAN to recalculate."
            textSize = 11.5f; setPadding(dp(6), dp(12), dp(6), dp(4))
        })
    }

    private fun addSection(title: String, results: List<StockResult>, copyButtons: Boolean) {
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        headerRow.addView(TextView(this).apply {
            text = title; textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        if (copyButtons) headerRow.addView(MaterialButton(this).apply {
            text = "COPY ALL"; textSize = 10f; minWidth = 0; minimumWidth = 0; setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { copySymbols(results.map { it.symbol }) }
        }, LinearLayout.LayoutParams(dp(86), dp(42)))
        resultArea.addView(headerRow)
        if (results.isEmpty()) {
            resultArea.addView(TextView(this).apply { text = "No stocks in this group."; textSize = 13f; setPadding(dp(8), 0, dp(8), dp(8)) })
            return
        }
        val table = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        table.addView(tableRow("STOCK", "DAYS", "UP", "DOWN", "UP D", "DOWN D", "RATIO", null, true))
        results.forEach { r ->
            val copy = if (copyButtons) MaterialButton(this).apply {
                text = "COPY"; textSize = 9.5f; minWidth = 0; minimumWidth = 0; setPadding(dp(3), 0, dp(3), 0)
                setOnClickListener { copySymbol(r.symbol) }
            } else null
            table.addView(tableRow(r.symbol, r.days.toString(), r.upDays.toString(), r.downDays.toString(),
                lakh(r.upDelivery), lakh(r.downDelivery), if (r.ratio.isInfinite()) "∞" else String.format(Locale.US, "%.2f×", r.ratio), copy, false))
        }
        val card = MaterialCardView(this).apply { radius = dp(14).toFloat(); strokeWidth = dp(1); setContentPadding(dp(4), dp(4), dp(4), dp(4)) }
        card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true; addView(table) })
        resultArea.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun tableRow(stock: String, days: String, up: String, down: String, upD: String, downD: String, ratio: String, copy: View?, header: Boolean): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(if (header) 34 else 44) }
        row.addView(cell(stock, if (header) dp(112) else dp(112), header, Gravity.START))
        row.addView(cell(days, dp(55), header, Gravity.CENTER))
        row.addView(cell(up, dp(48), header, Gravity.CENTER))
        row.addView(cell(down, dp(55), header, Gravity.CENTER))
        row.addView(cell(upD, dp(92), header, Gravity.CENTER))
        row.addView(cell(downD, dp(96), header, Gravity.CENTER))
        row.addView(cell(ratio, dp(74), header, Gravity.CENTER))
        if (copy != null) row.addView(copy, LinearLayout.LayoutParams(dp(68), dp(40)))
        return row
    }

    private fun cell(text: String, width: Int, header: Boolean, gravity: Int): TextView = TextView(this).apply {
        this.text = text; textSize = if (header) 9.5f else 12.5f; this.gravity = gravity; setPadding(dp(3), 0, dp(3), 0)
        if (!header && text.contains("×")) setTypeface(null, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(width, -1)
    }

    private fun addErrorSection(results: List<StockResult>) {
        resultArea.addView(TextView(this).apply { text = "NOT FOUND / NO DATA"; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(dp(6), dp(10), dp(6), dp(6)) })
        results.forEach { r -> resultArea.addView(TextView(this).apply { text = "${r.symbol}  —  ${r.error ?: "No NSE data"}"; textSize = 13f; setPadding(dp(8), dp(5), dp(8), dp(5)) }) }
    }

    private fun renderError(message: String) {
        clearResultViews()
        resultArea.addView(TextView(this).apply { text = "Analysis failed\n\n$message"; textSize = 16f; setPadding(dp(8), dp(16), dp(8), dp(16)) })
    }

    private fun clearResults() {
        clearResultViews()
        resultArea.addView(TextView(this).apply { text = "Results cleared. Enter stocks and tap SCAN."; textSize = 15f; setPadding(dp(8), dp(14), dp(8), dp(14)) })
    }

    private fun clearResultViews() {
        while (resultArea.childCount > 2) resultArea.removeViewAt(2)
    }

    private fun copySymbol(symbol: String) { copySymbols(listOf(symbol)) }

    private fun copySymbols(symbols: List<String>) {
        if (symbols.isEmpty()) { toast("No stocks to copy."); return }
        val text = symbols.distinct().joinToString("\n")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Stock symbols", text))
        toast(if (symbols.size == 1) "${symbols.first()} copied" else "${symbols.distinct().size} symbols copied")
    }

    private fun loadSaved() {
        prefs.getStringSet("saved1", emptySet())?.let { savedSymbols1.addAll(it) }
        prefs.getStringSet("saved2", emptySet())?.let { savedSymbols2.addAll(it) }
        // Keep the previous single-list data in the first list for users upgrading from the older app.
        if (savedSymbols1.isEmpty()) prefs.getStringSet("saved", emptySet())?.let { savedSymbols1.addAll(it) }
    }

    private fun saveSaved() {
        prefs.edit()
            .putStringSet("saved1", savedSymbols1.toSet())
            .putStringSet("saved2", savedSymbols2.toSet())
            .apply()
    }

    private fun saveLastSettings() {
        if (!::symbolInput.isInitialized) return
        prefs.edit().putString("symbols", symbolInput.text.toString()).putInt("period", periodSpinner.selectedItemPosition)
            .putInt("threshold", thresholdSpinner.selectedItemPosition).apply()
        fromDate?.let { prefs.edit().putLong("from", it.timeInMillis).apply() }
        toDate?.let { prefs.edit().putLong("to", it.timeInMillis).apply() }
    }

    private fun restoreLastSettings() {
        symbolInput.setText(prefs.getString("symbols", ""))
        periodSpinner.setSelection(prefs.getInt("period", 5).coerceIn(0, periodSpinner.count - 1))
        thresholdSpinner.setSelection(prefs.getInt("threshold", 1).coerceIn(0, thresholdSpinner.count - 1))
        prefs.getLong("from", 0L).takeIf { it > 0 }?.let { fromDate = Calendar.getInstance().apply { timeInMillis = it }; fromDateButton.text = "From: ${dateFormat.format(fromDate!!.time)}" }
        prefs.getLong("to", 0L).takeIf { it > 0 }?.let { toDate = Calendar.getInstance().apply { timeInMillis = it }; toDateButton.text = "To: ${dateFormat.format(toDate!!.time)}" }
    }

    private fun parseDouble(s: String) = s.trim().replace(",", "").toDoubleOrNull()
    private fun parseLong(s: String): Long? = s.trim().replace(",", "").toDoubleOrNull()?.toLong()
    private fun normalize(s: String) = s.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")

    private fun sameNseDate(value: String, requested: Calendar): Boolean {
        val target = requested.get(Calendar.YEAR) * 10000 + (requested.get(Calendar.MONTH) + 1) * 100 + requested.get(Calendar.DAY_OF_MONTH)
        for (fmt in nseDateFormats) {
            try {
                fmt.isLenient = false
                val parsed = fmt.parse(value.trim()) ?: continue
                val c = Calendar.getInstance().apply { time = parsed }
                val actual = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                if (actual == target) return true
            } catch (_: Exception) { }
        }
        return false
    }

    private fun parseCsv(line: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') { if (quoted && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else quoted = !quoted }
            else if (ch == ',' && !quoted) { out.add(cur.toString()); cur.setLength(0) }
            else cur.append(ch)
            i++
        }
        out.add(cur.toString()); return out
    }

    private fun lakh(v: Long): String = String.format(Locale.US, "%.2fL", v / 100000.0)
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 14f; setPadding(0, 0, 0, dp(4)) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    data class DayRow(val prevClose: Double, val close: Double, val delivery: Long)
    data class StockResult(val symbol: String, val days: Int = 0, val upDays: Int = 0, val downDays: Int = 0, val flatDays: Int = 0, val upDelivery: Long = 0, val downDelivery: Long = 0, val flatDelivery: Long = 0, val ratio: Double = 0.0, val error: String? = null)
    class MutableTotals(val symbol: String) { var days = 0; var upDays = 0; var downDays = 0; var flatDays = 0; var up = 0L; var down = 0L; var flat = 0L }
}
