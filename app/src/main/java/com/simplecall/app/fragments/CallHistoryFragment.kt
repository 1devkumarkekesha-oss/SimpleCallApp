package com.simplecall.app.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplecall.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallLogItem(val name: String, val number: String, val type: Int, val time: Long)

class CallHistoryFragment : Fragment() {

    private val logs = mutableListOf<CallLogItem>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private var loaded = false

    companion object {
        // Your code capped at 50 via "DESC LIMIT 50" inside the sortOrder
        // string. That isn't part of the query contract — providers may reject
        // or ignore it — so the cap is enforced while reading the cursor.
        private const val MAX_ENTRIES = 50
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_call_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        recyclerView.layoutManager = LinearLayoutManager(context)

        recyclerView.adapter = CallLogAdapter(logs) { log -> callBack(log.number) }

        loadCallLogs()
    }

    override fun onResume() {
        super.onResume()
        if (!loaded) loadCallLogs()
    }

    private fun loadCallLogs() {
        // FIX (your code): the query ran with no permission check while
        // MainActivity's permission request was still unanswered — the
        // CallLog query throws SecurityException in that state.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            emptyText.text = "Call history permission needed"
            updateEmptyState(forceEmpty = true)
            return
        }

        // Also moved off the main thread.
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { queryCallLogs() }
            logs.clear()
            logs.addAll(result)
            loaded = true
            emptyText.text = "No call history"
            recyclerView.adapter?.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun queryCallLogs(): List<CallLogItem> {
        val result = mutableListOf<CallLogItem>()
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val nameCol = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numCol = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
            if (numCol < 0 || typeCol < 0 || dateCol < 0) return emptyList()

            while (it.moveToNext() && result.size < MAX_ENTRIES) {
                val number = it.getString(numCol)?.trim().orEmpty()
                if (number.isEmpty()) continue
                // Your code showed "Unknown" whenever the cached name was
                // missing; the number itself is the better label.
                val name = if (nameCol >= 0) {
                    it.getString(nameCol)?.trim()?.takeIf { n -> n.isNotEmpty() } ?: number
                } else {
                    number
                }
                result.add(CallLogItem(name, number, it.getInt(typeCol), it.getLong(dateCol)))
            }
        }
        return result
    }

    private fun updateEmptyState(forceEmpty: Boolean = false) {
        val empty = forceEmpty || logs.isEmpty()
        emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun callBack(number: String) {
        // FIX (your code): no CALL_PHONE check and the exception was swallowed
        // silently, so a tap could do nothing with no feedback. Also the tel:
        // URI is now encoded (a raw '#' would truncate the number).
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Call permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class CallLogAdapter(
    private val logs: List<CallLogItem>,
    private val onClick: (CallLogItem) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textName)
        val number: TextView = view.findViewById(R.id.textNumber)
        val time: TextView = view.findViewById(R.id.textTime)
        val icon: ImageView = view.findViewById(R.id.callTypeIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val log = logs[position]
        holder.name.text = log.name
        holder.number.text = log.number
        holder.time.text = formatDate(log.time)
        holder.icon.setImageResource(
            when (log.type) {
                CallLog.Calls.INCOMING_TYPE -> android.R.drawable.stat_sys_phone_call
                CallLog.Calls.OUTGOING_TYPE -> android.R.drawable.stat_sys_phone_call_forward
                CallLog.Calls.MISSED_TYPE -> android.R.drawable.stat_notify_missed_call
                else -> android.R.drawable.stat_sys_phone_call
            }
        )
        holder.itemView.setOnClickListener { onClick(log) }
    }

    override fun getItemCount() = logs.size

    private fun formatDate(time: Long): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(time))
    }
}
