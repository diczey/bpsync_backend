package com.example.bpsync

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.bpsync.databinding.FragmentPulseRateBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class PulseRateFragment : Fragment() {

    private var _binding: FragmentPulseRateBinding? = null
    private val binding get() = _binding!!

    private var isMonitoring = true
    private var ecgTime = 0
    private var ecgDataSet: LineDataSet? = null
    private val ecgHandler = Handler(Looper.getMainLooper())

    private val ecgRunnable = object : Runnable {
        override fun run() {
            val b = _binding ?: return
            if (isMonitoring) {
                val value = generateEcgValue(ecgTime)
                ecgDataSet?.addEntry(Entry(ecgTime.toFloat(), value))
                ecgTime++

                // Cap dataset to avoid memory growth
                ecgDataSet?.let { ds ->
                    if (ds.entryCount > 300) {
                        val kept = (1 until ds.entryCount).map { ds.getEntryForIndex(it) }
                        ds.clear()
                        kept.forEachIndexed { i, e -> ds.addEntry(Entry(i.toFloat(), e.y)) }
                        ecgTime = kept.size
                    }
                }

                b.chartEcgWaveform.data?.notifyDataChanged()
                b.chartEcgWaveform.notifyDataSetChanged()
                b.chartEcgWaveform.setVisibleXRangeMaximum(50f)
                b.chartEcgWaveform.moveViewToX(ecgTime.toFloat())
            }
            ecgHandler.postDelayed(this, 100)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPulseRateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        binding.btnMonitorToggle.setOnClickListener {
            toggleMonitoring()
        }

        setupEcgChart()
        setupTodayChart()
        showDemoData()
        ecgHandler.post(ecgRunnable)
    }

    private fun toggleMonitoring() {
        isMonitoring = !isMonitoring
        if (isMonitoring) {
            binding.ivMonitorIcon.setImageResource(R.drawable.ic_pause_circle)
            binding.btnMonitorToggle.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_bp_red)
            binding.tvEcgStatus.text = "RECORDING"
            binding.viewLiveDot.visibility = View.VISIBLE
        } else {
            binding.ivMonitorIcon.setImageResource(R.drawable.ic_play_circle)
            binding.btnMonitorToggle.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_icon_green)
            binding.tvEcgStatus.text = "PAUSED"
            binding.viewLiveDot.visibility = View.INVISIBLE
        }
    }

    private fun generateEcgValue(index: Int): Float {
        val pos = index % 30
        return when {
            pos < 5  -> (Math.random() * 5).toFloat()
            pos == 5 -> 20f
            pos < 10 -> (Math.random() * 5).toFloat()
            pos == 10 -> -15f
            pos == 11 -> 80f
            pos == 12 -> -10f
            pos < 18 -> (Math.random() * 5).toFloat()
            pos == 18 -> 15f
            else -> (Math.random() * 5).toFloat()
        }
    }

    private fun setupEcgChart() {
        // Seed initial entries
        val initialEntries = (0 until 50).map { i ->
            Entry(i.toFloat(), generateEcgValue(i))
        }
        ecgTime = 50

        ecgDataSet = LineDataSet(initialEntries, "ECG").apply {
            color = Color.parseColor("#EC4899")
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            isHighlightEnabled = false
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.chartEcgWaveform.apply {
            data = LineData(ecgDataSet!!)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            setVisibleXRangeMaximum(50f)
            moveViewToX(ecgTime.toFloat())

            xAxis.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#20EC4899")
                setDrawAxisLine(false)
                setDrawLabels(false)
            }
            axisLeft.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(false)
                axisMinimum = -30f
                axisMaximum = 100f
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupTodayChart() {
        binding.chartTodayPulse.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            legend.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                axisLineColor = Color.parseColor("#E5E7EB")
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                granularity = 1f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                axisLineColor = Color.parseColor("#E5E7EB")
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                axisMinimum = 60f
                axisMaximum = 85f
            }
            axisRight.isEnabled = false
        }
    }

    private fun showDemoData() {
        val bpm = 72
        binding.tvPulseValue.text = bpm.toString()
        binding.tvPulseStatCurrent.text = bpm.toString()
        binding.tvPulseStatMin.text = "62"
        binding.tvPulseStatMax.text = "88"
        binding.tvRestingHR.text = "68 BPM"
        binding.tvAvgPulse7Day.text = "72"
        binding.tvPulseTimestamp.text = "2 hours ago"

        // 24-hour pattern chart
        val todayValues = listOf(68f, 65f, 63f, 64f, 66f, 68f, 72f)
        val entries = todayValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val dataSet = LineDataSet(entries, "BPM").apply {
            color = Color.parseColor("#EC4899")
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#EC4899")
            fillAlpha = 50
            isHighlightEnabled = false
        }
        binding.chartTodayPulse.data = LineData(dataSet)
        binding.chartTodayPulse.xAxis.valueFormatter = IndexAxisValueFormatter(
            arrayOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "23:59")
        )
        binding.chartTodayPulse.xAxis.labelCount = 7
        binding.chartTodayPulse.animateX(800)
        binding.chartTodayPulse.invalidate()

        // Weekly chart (hidden, compat)
        val weeklyEntries = listOf(72f, 70f, 74f, 71f, 73f, 69f, 72f)
            .mapIndexed { i, v -> Entry(i.toFloat(), v) }
        binding.chartWeeklyPulse.data = LineData(
            LineDataSet(weeklyEntries, "Weekly").apply {
                color = Color.parseColor("#EC4899")
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
            }
        )
        binding.chartWeeklyPulse.invalidate()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && !ecgHandler.hasMessages(0)) {
            ecgHandler.post(ecgRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        ecgHandler.removeCallbacks(ecgRunnable)
    }

    override fun onDestroyView() {
        ecgHandler.removeCallbacks(ecgRunnable)
        super.onDestroyView()
        _binding = null
    }
}
