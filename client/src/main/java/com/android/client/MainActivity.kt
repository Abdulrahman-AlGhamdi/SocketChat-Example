package com.android.client

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.client.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var clientSocket: Socket
    private lateinit var imm: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.connectClient.setOnClickListener {
            binding.connectClient.isEnabled = false
            binding.closeClient.isEnabled = true
            connectClient()
        }
        binding.closeClient.setOnClickListener {
            binding.closeClient.isEnabled = false
            binding.connectClient.isEnabled = true
            clientSocket.close()
        }
    }

    private fun connectClient() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            // Create client socket object
            clientSocket = Socket()

            // Connect the client socket to the server socket
            val socketAddress = InetSocketAddress("localhost", 1500)
            clientSocket.connect(socketAddress)
            addToChatText("Message: connected to the server")

            readAndWriteData()

        } catch (exception: Exception) {
            withContext(Dispatchers.Main) {
                binding.closeClient.isEnabled = false
                binding.connectClient.isEnabled = true
            }
            exception.localizedMessage?.let { addToChatText(it) }
            exception.printStackTrace()
        }
    }

    private suspend fun readAndWriteData(): Unit = withContext(Dispatchers.Main) {
        // Write to socket output stream
        binding.chatText.setOnEditorActionListener { _, _, _ ->
            imm.hideSoftInputFromWindow(binding.chatText.windowToken, 0)
            if (binding.chatText.text.isEmpty()) return@setOnEditorActionListener false

            val clientText = binding.chatText.text.toString()
            binding.chatText.text = null

            lifecycleScope.launch(Dispatchers.IO) {
                val outputStream = DataOutputStream(clientSocket.getOutputStream())
                outputStream.writeUTF(clientText)
            }

            true
        }

        // Read from socket input stream
        withContext(Dispatchers.IO) {
            while (!clientSocket.isClosed) {
                val textFromClient = DataInputStream(clientSocket.getInputStream()).readUTF()
                addToChatText("Server: $textFromClient")
            }
        }
    }

    private suspend fun addToChatText(text: String): Unit = withContext(Dispatchers.Main) {
        binding.chat.text = "${binding.chat.text}\n$text"
    }

    override fun onDestroy() {
        if (::clientSocket.isInitialized) clientSocket.close()
        super.onDestroy()
    }
}