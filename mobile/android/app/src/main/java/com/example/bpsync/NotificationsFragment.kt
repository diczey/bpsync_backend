package com.example.bpsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bpsync.databinding.FragmentNotificationsBinding
import com.example.bpsync.network.NotificationDto
import com.example.bpsync.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bildirimler listesi (backend getNotifications). */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val api get() = RetrofitClient.api

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.btnMarkAllRead.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    api.markAllNotificationsRead()
                    loadNotifications()
                    android.widget.Toast.makeText(requireContext(), "Tümü okundu işaretlendi", android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { }
            }
        }
        loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = api.getNotifications(limit = 50, unreadOnly = false)
                if (res.isSuccessful) {
                    val body = res.body()
                    val list = body?.notifications ?: emptyList()
                    binding.tvUnreadCount.text = "Okunmamış: ${body?.unreadCount ?: 0}"
                    if (list.isEmpty()) {
                        binding.tvNotificationsEmpty.visibility = View.VISIBLE
                        binding.recyclerNotifications.visibility = View.GONE
                    } else {
                        binding.tvNotificationsEmpty.visibility = View.GONE
                        binding.recyclerNotifications.visibility = View.VISIBLE
                        binding.recyclerNotifications.adapter = NotificationsAdapter(
                            list,
                            onMarkRead = { id -> markReadAndRefresh(id) },
                            onDelete = { id -> deleteAndRefresh(id) }
                        )
                    }
                } else {
                    binding.tvNotificationsEmpty.visibility = View.VISIBLE
                    binding.tvNotificationsEmpty.text = "Bildirimler yüklenemedi"
                }
            } catch (_: Exception) {
                binding.tvNotificationsEmpty.visibility = View.VISIBLE
                binding.tvNotificationsEmpty.text = "Bağlantı hatası"
            }
        }
    }

    private fun markReadAndRefresh(id: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                api.markNotificationRead(id)
                loadNotifications()
            } catch (_: Exception) { }
        }
    }

    private fun deleteAndRefresh(id: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Bildirimi sil")
            .setMessage("Bu bildirimi silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        api.deleteNotification(id)
                        loadNotifications()
                        android.widget.Toast.makeText(requireContext(), "Silindi", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}

class NotificationsAdapter(
    private val items: List<NotificationDto>,
    private val onMarkRead: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<NotificationsAdapter.Holder>() {

    private val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    class Holder(val view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val title = view.findViewById<TextView>(R.id.item_notification_title)
        val message = view.findViewById<TextView>(R.id.item_notification_message)
        val time = view.findViewById<TextView>(R.id.item_notification_time)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val n = items[position]
        holder.title.text = n.title
        holder.message.text = n.message
        holder.time.text = timeFormat.format(Date(n.timestamp))
        holder.itemView.setOnClickListener { onMarkRead(n.id) }
        holder.itemView.setOnLongClickListener {
            onDelete(n.id)
            true
        }
    }

    override fun getItemCount() = items.size
}
