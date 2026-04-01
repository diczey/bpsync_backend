package com.example.bpsync

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.FragmentPpgBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlin.math.sin

class PPGFragment : Fragment() {

    private var _binding: FragmentPpgBinding? = null
    private val binding get() = _binding!!

    private enum class Period { DAILY, WEEKLY, MONTHLY }
    private var selectedPeriod = Period.WEEKLY

    private val dailyData = listOf(
        Pair("00:00", 85f), Pair("04:00", 82f), Pair("08:00", 88f),
        Pair("12:00", 92f), Pair("16:00", 90f), Pair("20:00", 87f), Pair("23:59", 84f)
    )
    private val weeklyData = listOf(
        Pair("Mon", 86f), Pair("Tue", 84f), Pair("Wed", 88f),
        Pair("Thu", 87f), Pair("Fri", 89f), Pair("Sat", 85f), Pair("Sun", 86f)
    )
    private val monthlyData = listOf(
        Pair("Wk1", 85f), Pair("Wk2", 87f), Pair("Wk3", 86f), Pair("Wk4", 88f)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPpgBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        binding.btnPauseResume.setOnClickListener {
            // kept for compat
        }

        binding.btnPeriodDaily.setOnClickListener { selectPeriod(Period.DAILY) }
        binding.btnPeriodWeekly.setOnClickListener { selectPeriod(Period.WEEKLY) }
        binding.btnPeriodMonthly.setOnClickListener { selectPeriod(Period.MONTHLY) }

        setupTrendChart()
        setupWaveformChart()
        updateTrendChart(weeklyData)
        loadDemoStats()
    }

    private fun selectPeriod(period: Period) {
        selectedPeriod = period
        updatePeriodTabAppearance()
        when (period) {
            Period.DAILY -> {
                updateTrendChart(dailyData)
                binding.tvPpgStatAvg.text = "87"
                binding.tvPpgStatPeak.text = "92"
                binding.tvPpgStatLow.text = "82"
            }
            Period.WEEKLY -> {
                updateTrendChart(weeklyData)
                binding.tvPpgStatAvg.text = "87"
                binding.tvPpgStatPeak.text = "89"
                binding.tvPpgStatLow.text = "84"
            }
            Period.MONTHLY -> {
                updateTrendChart(monthlyData)
                binding.tvPpgStatAvg.text = "87"
                binding.tvPpgStatPeak.text = "88"
                binding.tvPpgStatLow.text = "85"
            }
        }
    }

    private fun updatePeriodTabAppearance() {
        val ctx = requireContext()
        val selectedBg = ContextCompat.getDrawable(ctx, R.drawable.bg_period_tab_selected)
        val selectedColor = Color.WHITE
        val unselectedColor = Color.parseColor("#6B7280")

        binding.btnPeriodDaily.background = if (selectedPeriod == Period.DAILY) selectedBg else null
        binding.btnPeriodWeekly.background = if (selectedPeriod == Period.WEEKLY) selectedBg else null
        binding.btnPeriodMonthly.background = if (selectedPeriod == Period.MONTHLY) selectedBg else null

        binding.btnPeriodDaily.setTextColor(if (selectedPeriod == Period.DAILY) selectedColor else unselectedColor)
        binding.btnPeriodWeekly.setTextColor(if (selectedPeriod == Period.WEEKLY) selectedColor else unselectedColor)
        binding.btnPeriodMonthly.setTextColor(if (selectedPeriod == Period.MONTHLY) selectedColor else unselectedColor)
    }

    private fun setupTrendChart() {
        binding.chartPpgTrend.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            legend.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            extraLeftOffset = 5f
            extraRightOffset = 10f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                gridLineWidth = 1f
                axisLineColor = Color.parseColor("#E5E7EB")
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                gridLineWidth = 1f
                axisLineColor = Color.parseColor("#E5E7EB")
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                axisMinimum = 75f
                axisMaximum = 95f
            }
            axisRight.isEnabled = false
        }
    }

    private fun updateTrendChart(data: List<Pair<String, Float>>) {
        val entries = data.mapIndexed { idx, pair -> Entry(idx.toFloat(), pair.second) }
        val labels = data.map { it.first }

        val dataSet = LineDataSet(entries, "PPG").apply {
            color = Color.parseColor("#06B6D4")
            lineWidth = 3f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#06B6D4")
            fillAlpha = 50
            setDrawValues(false)
        }

        binding.chartPpgTrend.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartPpgTrend.xAxis.labelCount = labels.size
        binding.chartPpgTrend.data = LineData(dataSet)
        binding.chartPpgTrend.animateX(600)
        binding.chartPpgTrend.invalidate()
    }

    private fun setupWaveformChart() {
        binding.chartPpgWaveform.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            legend.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.parseColor("#0D1117"))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1E2D3D")
                textColor = Color.parseColor("#4A5568")
                textSize = 9f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1E2D3D")
                textColor = Color.parseColor("#4A5568")
                textSize = 9f
                axisMinimum = -175f
                axisMaximum = 175f
            }
            axisRight.isEnabled = false
        }

        val waveEntries = (0..60).map { i ->
            Entry(i.toFloat(), (150 * sin(i * 0.3)).toFloat())
        }
        val waveSet = LineDataSet(waveEntries, "PPG Wave").apply {
            color = Color.parseColor("#00E5FF")
            lineWidth = 2f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
            setDrawFilled(false)
        }
        binding.chartPpgWaveform.data = LineData(waveSet)
        binding.chartPpgWaveform.invalidate()
    }

    private fun loadDemoStats() {
        binding.tvPpgAverage.text = "87"
        binding.tvPpgQuality.text = "94%"
        binding.tvPpgStatAvg.text = "87"
        binding.tvPpgStatPeak.text = "89"
        binding.tvPpgStatLow.text = "84"
        binding.tvPpgHeartRate.text = "72 BPM"
        binding.tvPpgSpo2.text = "98%"
        binding.tvSignalQuality.text = "90% Quality"
        binding.progressSignalQuality.progress = 90
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
