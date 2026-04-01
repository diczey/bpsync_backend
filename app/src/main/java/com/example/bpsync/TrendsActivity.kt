package com.example.bpsync

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.ActivityTrendsBinding
import com.example.bpsync.network.RetrofitClient
import com.example.bpsync.network.TrendDataPoint
import com.example.bpsync.network.TrendPointDto
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch

/**
 * Trendler ekranı. Grafik tamamen backend'den (getTrends) gelen veriyle doldurulur.
 */
class TrendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrendsBinding
    private val api get() = RetrofitClient.api
    private lateinit var trendTypes: List<Pair<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trendTypes = listOf("heart_rate" to "Kalp hızı", "systolic" to "Sistolik", "diastolic" to "Diyastolik", "spo2" to "SpO2")
        binding.spinnerType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            trendTypes.map { it.second }
        )

        loadTrends("heart_rate")

        binding.spinnerType.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                loadTrends(trendTypes[pos].first)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })
    }

    private fun loadTrends(type: String) {
        binding.tvTrendEmpty.visibility = View.VISIBLE
        binding.tvTrendEmpty.text = "Yükleniyor..."
        lifecycleScope.launch {
            try {
                val res = api.getTrends(period = "week")
                if (res.isSuccessful) {
                    val body = res.body()
                    val points = body?.points
                    val dataPoints = trendPointsToDataPoints(points, type)
                    val label = trendTypes.find { it.first == type }?.second ?: type
                    if (dataPoints.isNotEmpty()) {
                        binding.tvTrendEmpty.visibility = View.GONE
                        val avg = dataPoints.map { it.value }.average()
                        updateChart(dataPoints, label, avg, dataPoints.minOfOrNull { it.value } ?: 0.0, dataPoints.maxOfOrNull { it.value } ?: 0.0)
                    } else {
                        binding.tvTrendEmpty.text = body?.message ?: "Bu tür için henüz veri yok"
                    }
                } else {
                    binding.tvTrendEmpty.text = "Yüklenemedi"
                }
            } catch (e: Exception) {
                binding.tvTrendEmpty.text = "Hata: ${e.message}"
                Toast.makeText(this@TrendsActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun trendPointsToDataPoints(points: List<TrendPointDto>?, type: String): List<TrendDataPoint> {
        if (points.isNullOrEmpty()) return emptyList()
        return points.mapIndexed { i, p ->
            val value = when (type) {
                "heart_rate" -> p.avgHeartRate ?: 0.0
                "systolic" -> p.avgSystolic ?: 0.0
                "diastolic" -> p.avgDiastolic ?: 0.0
                "spo2" -> 0.0
                else -> p.avgHeartRate ?: 0.0
            }
            TrendDataPoint(i.toLong(), value)
        }
    }

    private fun updateChart(points: List<TrendDataPoint>, label: String, avg: Double, min: Double, max: Double) {
        val entries = points.mapIndexed { i, p -> Entry(i.toFloat(), p.value.toFloat()) }
        val set = LineDataSet(entries, label).apply {
            color = Color.parseColor("#1E88E5")
            setCircleColor(Color.parseColor("#1E88E5"))
            lineWidth = 2.5f
            setDrawValues(true)
        }
        binding.chartTrends.data = LineData(set)
        binding.chartTrends.description.text = "Ort: %.1f  Min: %.1f  Max: %.1f".format(avg, min, max)
        binding.chartTrends.invalidate()
    }
}
