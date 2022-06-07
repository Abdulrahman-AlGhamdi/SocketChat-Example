package com.android.server

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.server.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var serverSocket: ServerSocket
    private lateinit var clientSocket: Socket
    private lateinit var imm: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.openServer.setOnClickListener {
            binding.openServer.isEnabled = false
            binding.closeServer.isEnabled = true
            openServer()
        }
        binding.closeServer.setOnClickListener {
            binding.closeServer.isEnabled = false
            binding.openServer.isEnabled = true
            if (::clientSocket.isInitialized) clientSocket.close()
            if (::serverSocket.isInitialized) serverSocket.close()
        }
    }

    private fun openServer() = lifecycleScope.launch(Dispatchers.IO) {
        try {
            // Read from socket input stream
            serverSocket = ServerSocket() // Create server socket object

            // Bind server socket to specific address & port
            val socketAddress = InetSocketAddress("localhost", 1500)
            serverSocket.bind(socketAddress)
            addToChatText("Message: the server has been created and bound to ${serverSocket.localPort} port")

            // Wait for the client to connect & accept the connection
            clientSocket = serverSocket.accept()
            addToChatText("Message: client socket has been accepted")

            readAndWriteData(clientSocket)

        } catch (exception: Exception) {
            withContext(Dispatchers.Main) {
                binding.closeServer.isEnabled = false
                binding.openServer.isEnabled = true
                serverSocket.close()
            }
            exception.localizedMessage?.let { addToChatText(it) }
            exception.printStackTrace()
        }
    }

    private suspend fun readAndWriteData(client: Socket): Unit = withContext(Dispatchers.Main) {
        // Write to socket output stream
        binding.chatText.setOnEditorActionListener { _, _, _ ->
            imm.hideSoftInputFromWindow(binding.chatText.windowToken, 0)
            if (binding.chatText.text.isEmpty()) return@setOnEditorActionListener false

            val serverText = binding.chatText.text.toString()
            binding.chatText.text = null

            lifecycleScope.launch(Dispatchers.IO) {
                val outputStream = DataOutputStream(client.getOutputStream())
                outputStream.writeUTF(serverText)
            }

            true
        }

        // Read from socket input stream
        withContext(Dispatchers.IO) {
            while (!client.isClosed) {
                val textFromClient = DataInputStream(client.getInputStream()).readUTF()
                addToChatText("Client: $textFromClient")
            }
        }
    }

    private suspend fun addToChatText(text: String): Unit = withContext(Dispatchers.Main) {
        binding.chat.text = "${binding.chat.text}\n$text"
    }

    override fun onDestroy() {
        if (::clientSocket.isInitialized) clientSocket.close()
        if (::serverSocket.isInitialized) serverSocket.close()
        super.onDestroy()
    }
}