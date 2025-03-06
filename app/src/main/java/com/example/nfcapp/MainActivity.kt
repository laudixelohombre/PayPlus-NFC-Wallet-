package com.example.nfcapp

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nfcapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_LONG).show()
            return
        }

        // Check if NFC is enabled
        if (!nfcAdapter!!.isEnabled) {
            binding.statusText.text = "NFC is disabled. Please enable NFC in your device settings."
        } else {
            binding.statusText.text = "NFC is enabled. Ready to scan."
        }

        // Set up pending intent for NFC foreground dispatch
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )

        // Set up intent filters for NFC dispatch system
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("text/plain")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Failed to add MIME type.", e)
            }
        }
        intentFiltersArray = arrayOf(ndef)

        // Handle initial intent if app was started from an NFC scan
        intent.let { handleNfcIntent(it) }

        binding.clearButton.setOnClickListener {
            binding.nfcContent.text = ""
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch
        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFiltersArray,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action || 
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val ndef = Ndef.get(it)
                
                lifecycleScope.launch {
                    try {
                        ndef?.connect()
                        val ndefMessage = ndef?.cachedNdefMessage
                        
                        if (ndefMessage != null) {
                            val records = ndefMessage.records
                            val payload = records[0].payload
                            val textEncoding = if ((payload[0] and 128.toByte()) == 0.toByte()) "UTF-8" else "UTF-16"
                            val languageCodeLength = payload[0] and 0x3F
                            val text = String(
                                payload, 
                                languageCodeLength + 1, 
                                payload.size - languageCodeLength - 1, 
                                charset(textEncoding)
                            )
                            binding.nfcContent.append("Content: $text\n\n")
                        } else {
                            binding.nfcContent.append("Tag ID: ${bytesToHexString(it.id)}\n\n")
                        }
                        
                        ndef?.close()
                    } catch (e: Exception) {
                        binding.nfcContent.append("Error reading tag: ${e.message}\n\n")
                    }
                }
            }
        }
    }
    
    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(0xFF and b.toInt())
            if (hex.length == 1) {
                sb.append('0')
            }
            sb.append(hex)
        }
        return sb.toString()
    }
}