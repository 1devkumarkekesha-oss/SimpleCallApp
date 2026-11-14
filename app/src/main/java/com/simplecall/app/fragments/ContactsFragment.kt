package com.simplecall.app.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplecall.app.Contact
import com.simplecall.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ContactsAdapter
    private val contacts = mutableListOf<Contact>()
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyText = view.findViewById(R.id.emptyText)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = ContactsAdapter(mutableListOf()) { contact -> makeCall(contact.number) }
        recyclerView.adapter = adapter

        view.findViewById<EditText>(R.id.editSearch).addTextChangedListener {
            adapter.filter(it?.toString().orEmpty())
            updateEmptyState()
        }

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        // MainActivity's permission request is async: on first launch this
        // fragment is created BEFORE the user answers the dialog. Re-try when
        // the user comes back after granting.
        if (!loaded) loadContacts()
    }

    private fun loadContacts() {
        // FIX (your v2 code): the query ran with no permission check, while the
        // permission dialog was still on screen -> SecurityException / crash on
        // first launch. Guard here and show the empty state instead.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            emptyText.text = "Contacts permission needed"
            updateEmptyState(forceEmpty = true)
            return
        }

        // Also moved off the main thread (cursor over a large address book janks).
        viewLifecycleOwner.lifecycleScope.launch {
            val loadedContacts = withContext(Dispatchers.IO) { queryContacts() }
            contacts.clear()
            contacts.addAll(loadedContacts)
            adapter.submitAll(contacts)
            loaded = true
            emptyText.text = "No contacts found"
            updateEmptyState()
        }
    }

    private fun queryContacts(): List<Contact> {
        val result = mutableListOf<Contact>()
        val seenNumbers = mutableSetOf<String>()

        val cursor = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameCol < 0 || numCol < 0) return emptyList()
            while (it.moveToNext()) {
                val name = it.getString(nameCol)?.trim().orEmpty()
                val number = it.getString(numCol)?.trim().orEmpty()
                if (number.isEmpty()) continue
                // De-duplicate by number (your v2 key was name+number, which kept
                // the same number twice under slightly different names).
                val key = number.replace(Regex("[\\s\\-()]"), "")
                if (seenNumbers.add(key)) {
                    result.add(Contact(name.ifEmpty { number }, number))
                }
            }
        }
        return result
    }

    private fun updateEmptyState(forceEmpty: Boolean = false) {
        val empty = forceEmpty || adapter.itemCount == 0
        emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Call permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                // Uri.encode: a raw '#' in the number would truncate the tel: URI.
                data = Uri.parse("tel:${Uri.encode(number)}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class ContactsAdapter(
    private var allContacts: List<Contact>,
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.VH>() {

    private var visibleContacts: List<Contact> = allContacts

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textName)
        val number: TextView = view.findViewById(R.id.textNumber)
    }

    fun submitAll(contacts: List<Contact>) {
        allContacts = contacts
        visibleContacts = contacts
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim()
        visibleContacts = if (q.isEmpty()) allContacts else allContacts.filter {
            it.name.contains(q, ignoreCase = true) || it.number.contains(q)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = visibleContacts[position]
        holder.name.text = c.name
        holder.number.text = c.number
        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount() = visibleContacts.size
}
