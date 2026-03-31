package com.example.bpsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.bpsync.databinding.FragmentReportsBinding
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Haftalık ve aylık raporlar (reports/weekly, reports/monthly).
 */
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val weekLabels = listOf("Bu hafta", "Geçen hafta", "2 hafta önce", "3 hafta önce")
        val monthLabels = listOf("Bu ay", "Geçen ay", "2 ay önce", "3 ay önce")
        binding.spinnerWeekOffset.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, weekLabels)
        binding.spinnerMonthOffset.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, monthLabels)
        binding.btnLoadWeekly.setOnClickListener { loadWeekly() }
        binding.btnLoadMonthly.setOnClickListener { loadMonthly() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadWeekly() {
        val offset = binding.spinnerWeekOffset.selectedItemPosition
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getWeeklyReport(weekOffset = offset)
                if (res.isSuccessful) {
                    val r = res.body() ?: return@launch
                    binding.tvWeeklyReport.visibility = View.VISIBLE
                    binding.tvWeeklyReport.text = if (r.success != false && (r.report != null || r.weekStart != null)) formatReport(r) else (r.message ?: "Bu hafta için veri yok")
                } else {
                    Toast.makeText(requireContext(), "Rapor alınamadı", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMonthly() {
        val offset = binding.spinnerMonthOffset.selectedItemPosition
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getMonthlyReport(monthOffset = offset)
                if (res.isSuccessful) {
                    val r = res.body() ?: return@launch
                    binding.tvMonthlyReport.visibility = View.VISIBLE
                    binding.tvMonthlyReport.text = if (r.success != false && (r.report != null || r.weekStart != null)) formatReport(r) else (r.message ?: "Bu ay için veri yok")
                } else {
                    Toast.makeText(requireContext(), "Rapor alınamadı", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatReport(r: com.example.bpsync.network.WeeklyReportResponse): String {
        // Backend { success, report: { ... } } formatı; yoksa flat alanlar (eski uyumluluk)
        val report = r.report
        val weekStart = report?.weekStart ?: r.weekStart
        val weekEnd = report?.weekEnd ?: r.weekEnd
        val avgHeartRate = report?.avgHeartRate ?: r.avgHeartRate
        val avgSystolic = report?.avgSystolic ?: r.avgSystolic
        val avgDiastolic = report?.avgDiastolic ?: r.avgDiastolic
        val readingsCount = report?.readingsCount ?: r.readingsCount
        val healthScore = report?.healthScore ?: r.healthScore
        val recommendations = report?.recommendations ?: r.recommendations
        val sb = StringBuilder()
        weekStart?.let { sb.append("Dönem: $it") }
        weekEnd?.let { sb.append(" - $it\n") }
        sb.append("Ort. kalp atışı: ${avgHeartRate ?: "-"}\n")
        sb.append("Ort. sistolik: ${avgSystolic ?: "-"}\n")
        sb.append("Ort. diyastolik: ${avgDiastolic ?: "-"}\n")
        sb.append("Ölçüm sayısı: ${readingsCount ?: 0}\n")
        healthScore?.let { sb.append("Sağlık skoru: $it\n") }
        recommendations?.takeIf { it.isNotEmpty() }?.let { rec ->
            sb.append("Öneriler: ${rec.joinToString(", ")}\n")
        }
        report?.dailySummaries?.takeIf { it.isNotEmpty() }?.let { daily ->
            sb.append("Günlük özet: ")
            daily.take(7).joinTo(sb) { "${it.date.take(10)}: ${it.avgSystolic?.toInt() ?: "-"}/${it.avgDiastolic?.toInt() ?: "-"}" }
        }
        return sb.toString()
    }
}
