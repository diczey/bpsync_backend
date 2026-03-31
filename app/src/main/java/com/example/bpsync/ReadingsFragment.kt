package com.example.bpsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bpsync.databinding.FragmentReadingsBinding
import com.example.bpsync.network.BPCalibrationRequest
import com.example.bpsync.network.BPPredictionRequest
import com.example.bpsync.network.BPReadingDto
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ReadingsFragment : Fragment() {

    private var _binding: FragmentReadingsBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
            (activity as? HomeActivity)?.showDashboard()
        }

        binding.recyclerReadings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReadings.adapter = ReadingsAdapter(emptyList()) { item -> showReadingDetail(item) }
        loadReadings()

        // Keep backend features functional (hidden buttons)
        binding.btnAddReading.setOnClickListener {
            Toast.makeText(requireContext(), "Manual readings not supported in this version.", Toast.LENGTH_SHORT).show()
        }
        binding.btnPredict.setOnClickListener { predictBp() }
        binding.btnCalibrate.setOnClickListener { calibrateBp() }
        binding.btnModelInfo.setOnClickListener { loadModelInfo() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadReadings() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getReadings(limit = 20)
                if (res.isSuccessful) {
                    val list = res.body()?.readings ?: emptyList()
                    binding.recyclerReadings.adapter = ReadingsAdapter(list) { item -> showReadingDetail(item) }
                    updateStats(list)
                } else {
                    showDemoData()
                }
            } catch (_: Exception) {
                showDemoData()
            }
        }
    }

    private fun showDemoData() {
        val demo = listOf(
            BPReadingDto(time = "2026-03-11T09:30:00", userId = "demo", systolic = 120, diastolic = 80, heartRate = 72, category = "Normal"),
            BPReadingDto(time = "2026-03-11T14:15:00", userId = "demo", systolic = 125, diastolic = 84, heartRate = 75, category = "Normal"),
            BPReadingDto(time = "2026-03-10T08:45:00", userId = "demo", systolic = 118, diastolic = 78, heartRate = 70, category = "Normal"),
            BPReadingDto(time = "2026-03-10T18:30:00", userId = "demo", systolic = 128, diastolic = 86, heartRate = 78, category = "Elevated"),
            BPReadingDto(time = "2026-03-09T10:00:00", userId = "demo", systolic = 122, diastolic = 82, heartRate = 74, category = "Normal"),
        )
        binding.recyclerReadings.adapter = ReadingsAdapter(demo) { item -> showReadingDetail(item) }
        updateStats(demo)
    }

    private fun updateStats(list: List<BPReadingDto>) {
        binding.tvStatTotal.text = list.size.toString()
        val normalCount = list.count { it.category?.lowercase()?.contains("normal") == true }
        binding.tvStatNormal.text = normalCount.toString()
        val alertCount = list.count {
            val cat = it.category?.lowercase() ?: ""
            cat.contains("elevated") || cat.contains("high")
        }
        binding.tvStatAlert.text = alertCount.toString()
    }

    private fun showReadingDetail(item: BPReadingDto) {
        val msg = buildString {
            append("Date: ${item.time}\n")
            append("BP: ${item.systolic ?: "-"}/${item.diastolic ?: "-"} mmHg\n")
            append("Pulse: ${item.heartRate ?: "-"} bpm\n")
            if (item.ptt != null) append("PTT: ${item.ptt} ms\n")
            if (item.quality != null) append("Quality: ${item.quality}\n")
            append("Status: ${item.category ?: "-"}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Measurement Detail")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun predictBp() {
        val ptt = binding.etPredictPtt.text.toString().toDoubleOrNull() ?: 0.0
        val hr = binding.etPredictHr.text.toString().toDoubleOrNull() ?: 0.0
        val age = binding.etPredictAge.text.toString().toDoubleOrNull() ?: 40.0
        if (ptt <= 0 || hr <= 0) {
            Toast.makeText(requireContext(), "Please enter PTT and heart rate", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.predictBp(BPPredictionRequest(ptt = ptt, heartRate = hr, age = age))
                if (res.isSuccessful && res.body()?.success == true) {
                    val b = res.body()!!
                    binding.tvPredictResult.visibility = View.VISIBLE
                    binding.tvPredictResult.text = "${b.systolic}/${b.diastolic} mmHg — ${b.category}"
                } else {
                    Toast.makeText(requireContext(), res.body()?.message ?: "Prediction failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calibrateBp() {
        val sys = binding.etCalSystolic.text.toString().toIntOrNull() ?: 0
        val dia = binding.etCalDiastolic.text.toString().toIntOrNull() ?: 0
        val ptt = binding.etCalPtt.text.toString().toDoubleOrNull() ?: 0.0
        val hr = binding.etCalHr.text.toString().toDoubleOrNull() ?: 0.0
        if (sys <= 0 || dia <= 0 || ptt <= 0 || hr <= 0) {
            Toast.makeText(requireContext(), "Please fill all calibration fields", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.calibrateBp(BPCalibrationRequest(measuredSystolic = sys, measuredDiastolic = dia, ptt = ptt, heartRate = hr))
                if (res.isSuccessful && res.body()?.success == true) {
                    binding.tvCalibrateResult.visibility = View.VISIBLE
                    binding.tvCalibrateResult.text = res.body()?.message ?: "Calibration complete"
                } else {
                    Toast.makeText(requireContext(), res.body()?.message ?: "Calibration failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadModelInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getModelInfo()
                if (res.isSuccessful && res.body()?.success == true) {
                    val m = res.body()!!
                    val sb = StringBuilder()
                    sb.append("Model loaded: ${if (m.modelLoaded) "Yes" else "No"}\n")
                    sb.append("Calibration: ${if (m.hasCalibration) "Available" else "None"}\n")
                    m.featureImportance?.forEach { (k, v) -> sb.append("$k: $v\n") }
                    binding.tvModelInfo.visibility = View.VISIBLE
                    binding.tvModelInfo.text = sb.toString().ifEmpty { "No data" }
                } else {
                    Toast.makeText(requireContext(), "Could not load model info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private class ReadingsAdapter(
    private val items: List<BPReadingDto>,
    private val onItemClick: (BPReadingDto) -> Unit
) : RecyclerView.Adapter<ReadingsAdapter.Holder>() {

    private val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.item_reading_date)
        val time: TextView = view.findViewById(R.id.item_reading_time)
        val bp: TextView = view.findViewById(R.id.item_reading_bp)
        val hr: TextView = view.findViewById(R.id.item_reading_hr)
        val status: TextView = view.findViewById(R.id.item_reading_status)
        val systolicDetail: TextView = view.findViewById(R.id.item_reading_systolic_detail)
        val diastolicDetail: TextView = view.findViewById(R.id.item_reading_diastolic_detail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reading, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val r = items[position]

        // Parse and format date/time
        try {
            val parsed = inputFmt.parse(r.time)
            holder.date.text = if (parsed != null) dateFmt.format(parsed) else r.time
            holder.time.text = if (parsed != null) timeFmt.format(parsed) else ""
        } catch (_: Exception) {
            holder.date.text = r.time
            holder.time.text = ""
        }

        // BP readings
        val sys = r.systolic
        val dia = r.diastolic
        holder.bp.text = if (sys != null && dia != null) "$sys/$dia" else "--/--"
        holder.hr.text = r.heartRate?.toString() ?: "--"

        // Systolic / Diastolic details
        holder.systolicDetail.text = buildString {
            append("Systolic: ")
            append("<b>${sys ?: "--"}</b> mmHg")
        }.let {
            android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT)
        }
        holder.diastolicDetail.text = buildString {
            append("Diastolic: ")
            append("<b>${dia ?: "--"}</b> mmHg")
        }.let {
            android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT)
        }

        // Status badge
        val cat = r.category?.lowercase() ?: "normal"
        val (statusText, bgRes) = when {
            cat.contains("elevated") -> Pair("Elevated", R.drawable.bg_badge_status_elevated)
            cat.contains("high") -> Pair("High", R.drawable.bg_badge_status_high)
            else -> Pair("Normal", R.drawable.bg_badge_status_normal)
        }
        holder.status.text = statusText
        holder.status.background = ContextCompat.getDrawable(holder.itemView.context, bgRes)

        holder.itemView.setOnClickListener { onItemClick(r) }
    }

    override fun getItemCount() = items.size
}
