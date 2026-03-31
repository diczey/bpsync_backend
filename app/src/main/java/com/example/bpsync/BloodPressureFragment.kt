package com.example.bpsync

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.FragmentBloodPressureBinding
import com.example.bpsync.network.AuthTokenProvider
import com.example.bpsync.network.RetrofitClient
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

class BloodPressureFragment : Fragment() {

    private var _binding: FragmentBloodPressureBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBloodPressureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        setupChart()
        loadData()
    }

    private fun setupChart() {
        binding.chartBP7Day.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            legend.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            extraBottomOffset = 8f
            extraLeftOffset = 4f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                gridLineWidth = 0.8f
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                valueFormatter = IndexAxisValueFormatter(
                    arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                )
                setDrawAxisLine(false)
                granularity = 1f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E5E7EB")
                gridLineWidth = 0.8f
                textColor = Color.parseColor("#6B7280")
                textSize = 11f
                axisMinimum = 70f
                axisMaximum = 130f
                setDrawAxisLine(false)
            }
            axisRight.isEnabled = false
        }
    }

    private fun loadData() {
        if (AuthTokenProvider.token == "demo") {
            showDemoData()
            return
        }
        lifecycleScope.launch {
            try {
                val res = RetrofitClient.api.getDashboardSummary()
                if (res.isSuccessful) {
                    val s = res.body()?.summary
                    s?.let {
                        binding.tvSystolic.text = it.latestSystolic?.toString() ?: "--"
                        binding.tvDiastolic.text = it.latestDiastolic?.toString() ?: "--"
                        val status = it.healthStatus ?: it.category ?: "Normal"
                        binding.tvBpStatus.text = status
                    } ?: showDemoData()
                } else showDemoData()
            } catch (_: Exception) {
                showDemoData()
            }
        }
    }

    private fun showDemoData() {
        binding.tvSystolic.text = "120"
        binding.tvDiastolic.text = "80"
        binding.tvAvgSystolic.text = "120"
        binding.tvAvgDiastolic.text = "80"
        binding.tvBpTimestamp.text = "2 hours ago"
        binding.tvBpStatus.text = "Normal"

        val systolicValues = listOf(120f, 118f, 122f, 119f, 121f, 117f, 120f)
        val diastolicValues = listOf(80f, 78f, 82f, 79f, 81f, 77f, 80f)

        val systolicEntries = systolicValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val diastolicEntries = diastolicValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }

        val systolicSet = LineDataSet(systolicEntries, "Systolic").apply {
            color = Color.parseColor("#EF4444")
            setCircleColor(Color.parseColor("#EF4444"))
            circleHoleColor = Color.WHITE
            lineWidth = 3f
            circleRadius = 4.5f
            circleHoleRadius = 2f
            setDrawFilled(true)
            fillColor = Color.parseColor("#EF4444")
            fillAlpha = 40
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.25f
            setDrawValues(false)
            highLightColor = Color.parseColor("#EF4444")
        }

        val diastolicSet = LineDataSet(diastolicEntries, "Diastolic").apply {
            color = Color.parseColor("#EC4899")
            setCircleColor(Color.parseColor("#EC4899"))
            circleHoleColor = Color.WHITE
            lineWidth = 3f
            circleRadius = 4.5f
            circleHoleRadius = 2f
            setDrawFilled(true)
            fillColor = Color.parseColor("#EC4899")
            fillAlpha = 35
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.25f
            setDrawValues(false)
            highLightColor = Color.parseColor("#EC4899")
        }

        binding.chartBP7Day.data = LineData(systolicSet, diastolicSet)
        binding.chartBP7Day.animateX(600)
        binding.chartBP7Day.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
