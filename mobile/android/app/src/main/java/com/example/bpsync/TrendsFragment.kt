package com.example.bpsync

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.FragmentTrendsBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.RetrofitClient
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

class TrendsFragment : Fragment() {

    private var _binding: FragmentTrendsBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api
    private var currentPeriod = "week"

    // Demo data sets keyed by period
    private val demoLabels = mapOf(
        "day"   to arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
        "week"  to arrayOf("Wk 1", "Wk 2", "Wk 3", "Wk 4"),
        "month" to arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun")
    )

    private val demoSystolic = mapOf(
        "day"   to listOf(120f, 118f, 122f, 119f, 121f, 117f, 120f),
        "week"  to listOf(120f, 119f, 121f, 118f),
        "month" to listOf(120f, 119f, 121f, 118f, 120f, 119f)
    )

    private val demoDiastolic = mapOf(
        "day"   to listOf(80f, 78f, 82f, 79f, 81f, 77f, 80f),
        "week"  to listOf(80f, 79f, 81f, 78f),
        "month" to listOf(80f, 79f, 81f, 78f, 80f, 79f)
    )

    private val demoPulse = mapOf(
        "day"   to listOf(72f, 70f, 74f, 71f, 73f, 70f, 72f),
        "week"  to listOf(72f, 71f, 73f, 70f),
        "month" to listOf(72f, 71f, 73f, 70f, 72f, 71f)
    )

    private val demoSpO2 = mapOf(
        "day"   to listOf(98f, 97f, 98f, 99f, 98f, 97f, 98f),
        "week"  to listOf(98f, 97f, 99f, 98f),
        "month" to listOf(98f, 97f, 98f, 99f, 98f, 97f)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        setupTabs()
        setupCharts()

        if (AuthTokenProvider.token == "demo") {
            showDemoData()
        } else {
            loadTrend("week")
        }
    }

    private fun setupTabs() {
        fun updateTabs(period: String) {
            currentPeriod = period

            val selectedBg = requireContext().getDrawable(R.drawable.bg_trend_tab_selected)
            val selectedColor = Color.WHITE
            val unselectedColor = requireContext().getColor(R.color.text_secondary)

            binding.tabDaily.background   = if (period == "day")   selectedBg else null
            binding.tabWeekly.background  = if (period == "week")  selectedBg else null
            binding.tabMonthly.background = if (period == "month") selectedBg else null

            binding.tabDaily.setTextColor(if (period == "day")   selectedColor else unselectedColor)
            binding.tabWeekly.setTextColor(if (period == "week")  selectedColor else unselectedColor)
            binding.tabMonthly.setTextColor(if (period == "month") selectedColor else unselectedColor)

            binding.tabDaily.setTypeface(null,   if (period == "day")   android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            binding.tabWeekly.setTypeface(null,  if (period == "week")  android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            binding.tabMonthly.setTypeface(null, if (period == "month") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            // Update labels on all charts
            val labels = demoLabels[period] ?: demoLabels["week"]!!
            listOf(binding.chartBP, binding.chartPulse, binding.chartSpO2).forEach { chart ->
                chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                chart.notifyDataSetChanged()
                chart.invalidate()
            }
        }

        binding.tabDaily.setOnClickListener {
            updateTabs("day")
            if (AuthTokenProvider.token == "demo") showDemoData() else loadTrend("day")
        }
        binding.tabWeekly.setOnClickListener {
            updateTabs("week")
            if (AuthTokenProvider.token == "demo") showDemoData() else loadTrend("week")
        }
        binding.tabMonthly.setOnClickListener {
            updateTabs("month")
            if (AuthTokenProvider.token == "demo") showDemoData() else loadTrend("month")
        }
    }

    private fun setupCharts() {
        listOf(binding.chartBP, binding.chartPulse, binding.chartSpO2).forEach { chart ->
            chart.apply {
                description.isEnabled = false
                setTouchEnabled(true)
                legend.isEnabled = false
                setDrawGridBackground(false)
                setExtraOffsets(0f, 8f, 0f, 8f)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#F0F4FF")
                    textColor = Color.parseColor("#9CA3AF")
                    textSize = 10f
                    axisLineColor = Color.parseColor("#E5E7EB")
                    setDrawAxisLine(true)
                    setDrawGridLines(false)
                    granularity = 1f
                }
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#F0F4FF")
                    textColor = Color.parseColor("#9CA3AF")
                    textSize = 10f
                    axisLineColor = Color.parseColor("#E5E7EB")
                }
                axisRight.isEnabled = false
            }
        }

        // SpO2 y-axis domain
        binding.chartSpO2.axisLeft.apply {
            axisMinimum = 94f
            axisMaximum = 101f
        }

        // Pulse y-axis domain
        binding.chartPulse.axisLeft.apply {
            axisMinimum = 58f
            axisMaximum = 82f
        }
    }

    private fun loadTrend(period: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getTrends(period = period)
                val points = res.body()?.points
                if (!points.isNullOrEmpty()) {
                    val systolicEntries  = points.mapIndexed { i, p -> Entry(i.toFloat(), (p.avgSystolic  ?: 0.0).toFloat()) }
                    val diastolicEntries = points.mapIndexed { i, p -> Entry(i.toFloat(), (p.avgDiastolic ?: 0.0).toFloat()) }
                    val pulseEntries     = points.mapIndexed { i, p -> Entry(i.toFloat(), (p.avgHeartRate ?: 0.0).toFloat()) }

                    updateBPChart(systolicEntries, diastolicEntries)
                    updatePulseChart(pulseEntries)
                    // SpO2 not in API — fall back to demo
                    updateSpO2Chart(demoSpO2[currentPeriod]!!.mapIndexed { i, v -> Entry(i.toFloat(), v) })

                    val avgSys   = points.mapNotNull { it.avgSystolic  }.average()
                    val avgDia   = points.mapNotNull { it.avgDiastolic }.average()
                    val avgPulse = points.mapNotNull { it.avgHeartRate }.average()
                    binding.tvAvgSystolic.text  = avgSys.toInt().toString()
                    binding.tvAvgDiastolic.text = avgDia.toInt().toString()
                    binding.tvAvgPulse.text     = avgPulse.toInt().toString()
                    binding.tvAvgSpO2.text      = "98"
                } else {
                    showDemoData()
                }
            } catch (_: Exception) {
                showDemoData()
            }
        }
    }

    private fun showDemoData() {
        val sys  = demoSystolic[currentPeriod]!!
        val dia  = demoDiastolic[currentPeriod]!!
        val pls  = demoPulse[currentPeriod]!!
        val spo2 = demoSpO2[currentPeriod]!!

        val systolicEntries  = sys.mapIndexed  { i, v -> Entry(i.toFloat(), v) }
        val diastolicEntries = dia.mapIndexed  { i, v -> Entry(i.toFloat(), v) }
        val pulseEntries     = pls.mapIndexed  { i, v -> Entry(i.toFloat(), v) }
        val spo2Entries      = spo2.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        // Update x-axis labels
        val labels = demoLabels[currentPeriod] ?: demoLabels["week"]!!
        listOf(binding.chartBP, binding.chartPulse, binding.chartSpO2).forEach { chart ->
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        }

        updateBPChart(systolicEntries, diastolicEntries)
        updatePulseChart(pulseEntries)
        updateSpO2Chart(spo2Entries)

        binding.tvAvgSystolic.text  = sys.average().toInt().toString()
        binding.tvAvgDiastolic.text = dia.average().toInt().toString()
        binding.tvAvgPulse.text     = pls.average().toInt().toString()
        binding.tvAvgSpO2.text      = spo2.average().toInt().toString()
    }

    private fun updateBPChart(systolicEntries: List<Entry>, diastolicEntries: List<Entry>) {
        val systolicSet = LineDataSet(systolicEntries, "Systolic").apply {
            color = Color.parseColor("#EF4444")
            setCircleColor(Color.parseColor("#EF4444"))
            lineWidth = 2.5f
            circleRadius = 3f
            setDrawCircles(true)
            setDrawFilled(true)
            fillColor = Color.parseColor("#EF4444")
            fillAlpha = 40
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        val diastolicSet = LineDataSet(diastolicEntries, "Diastolic").apply {
            color = Color.parseColor("#EC4899")
            setCircleColor(Color.parseColor("#EC4899"))
            lineWidth = 2.5f
            circleRadius = 3f
            setDrawCircles(true)
            setDrawFilled(true)
            fillColor = Color.parseColor("#EC4899")
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        binding.chartBP.data = LineData(systolicSet, diastolicSet)
        binding.chartBP.animateX(800)
    }

    private fun updatePulseChart(pulseEntries: List<Entry>) {
        val pulseSet = LineDataSet(pulseEntries, "BPM").apply {
            color = Color.parseColor("#EC4899")
            setCircleColor(Color.parseColor("#EC4899"))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircles(true)
            setDrawFilled(true)
            fillColor = Color.parseColor("#EC4899")
            fillAlpha = 40
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        binding.chartPulse.data = LineData(pulseSet)
        binding.chartPulse.animateX(800)
    }

    private fun updateSpO2Chart(spo2Entries: List<Entry>) {
        val spo2Set = LineDataSet(spo2Entries, "SpO2").apply {
            color = Color.parseColor("#06B6D4")
            setCircleColor(Color.parseColor("#06B6D4")  )
            circleHoleColor = Color.WHITE
            lineWidth = 2.5f
            circleRadius = 4f
            circleHoleRadius = 2f
            setDrawCircles(true)
            setDrawFilled(false)
            mode = LineDataSet.Mode.LINEAR
            setDrawValues(false)
        }
        binding.chartSpO2.data = LineData(spo2Set)
        binding.chartSpO2.animateX(800)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
