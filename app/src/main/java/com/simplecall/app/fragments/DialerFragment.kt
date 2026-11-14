package com.simplecall.app.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.simplecall.app.R

class DialerFragment : Fragment() {

    private lateinit var numberInput: EditText
    private val numberBuilder = StringBuilder()
    private var syncing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_dialer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        numberInput = view.findViewById(R.id.numberInput)

        // FIX (your v2 code): numberBuilder and the EditText could drift apart —
        // if the user typed/pasted into the field directly, Backspace and Call
        // then acted on a different string than the one on screen. Keep them
        // in sync both ways.
        numberInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                numberBuilder.clear()
                numberBuilder.append(s?.toString().orEmpty())
            }
        })

        val buttons = mapOf(
            R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3",
            R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6",
            R.id.btn7 to "7", R.id.btn8 to "8", R.id.btn9 to "9",
            R.id.btnStar to "*", R.id.btn0 to "0", R.id.btnHash to "#"
        )

        buttons.forEach { (id, digit) ->
            view.findViewById<View>(id).setOnClickListener {
                numberBuilder.append(digit)
                showNumber()
            }
        }

        // Standard dialer behaviour: long-press 0 enters '+'.
        view.findViewById<View>(R.id.btn0).setOnLongClickListener {
            numberBuilder.append("+")
            showNumber()
            true
        }

        view.findViewById<ImageButton>(R.id.btnCall).setOnClickListener {
            val number = numberInput.text.toString().trim()
            if (number.isNotEmpty()) {
                makeCall(number)
            } else {
                Toast.makeText(context, "Enter a number", Toast.LENGTH_SHORT).show()
            }
        }

        val backspace = view.findViewById<ImageButton>(R.id.btnBackspace)
        backspace.setOnClickListener {
            if (numberBuilder.isNotEmpty()) {
                numberBuilder.deleteCharAt(numberBuilder.length - 1)
                showNumber()
            }
        }
        backspace.setOnLongClickListener {
            numberBuilder.clear()
            showNumber()
            true
        }
    }

    private fun showNumber() {
        syncing = true
        numberInput.setText(numberBuilder.toString())
        numberInput.setSelection(numberInput.text.length)
        syncing = false
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
                // FIX (your v2 code): Uri.parse("tel:$number") treats '#' as a URI
                // fragment separator, so any number containing '#' (exactly what
                // the # key enters) was silently truncated. Encode it.
                data = Uri.parse("tel:${Uri.encode(number)}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
