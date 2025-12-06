package com.devaki.app.net

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "DEVAKI"

class TcpClient {
    
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var onConnectionChange: ((Boolean, String) -> Unit)? = null
    
    /**
     * Connect to TCP server with timeout.
     * @param host IP address or hostname
     * @param port Port number
     * @param timeoutMs Connection timeout in milliseconds (default 3000ms)
     */
    suspend fun connect(host: String, port: Int, timeoutMs: Int = 3000) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to $host:$port...")
            withContext(Dispatchers.Main) {
                onConnectionChange?.invoke(false, "Connecting...")
            }
            
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), timeoutMs)
            writer = BufferedWriter(OutputStreamWriter(socket?.getOutputStream()))
            
            Log.d(TAG, "Connected to $host:$port")
            withContext(Dispatchers.Main) {
                onConnectionChange?.invoke(true, "Connected to $host:$port")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            withContext(Dispatchers.Main) {
                onConnectionChange?.invoke(false, "Connection failed: ${e.message}")
            }
            close()
        }
    }
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return socket?.isConnected == true && socket?.isClosed == false
    }
    
    /**
     * Send a line of text to the server.
     * Automatically appends '\n' and flushes.
     * Safe to call even if not connected.
     */
    suspend fun sendLine(line: String) = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot send: not connected")
            return@withContext
        }
        
        try {
            synchronized(this@TcpClient) {
                writer?.write(line)
                writer?.write("\n")
                writer?.flush()
            }
            Log.d(TAG, "Sent: $line")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending line", e)
            withContext(Dispatchers.Main) {
                onConnectionChange?.invoke(false, "Connection lost")
            }
            close()
        }
    }
    
    /**
     * Close the connection safely.
     */
    fun close() {
        try {
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection", e)
        } finally {
            writer = null
            socket = null
            Log.d(TAG, "TCP connection closed")
        }
    }
    
    /**
     * Cleanup resources.
     */
    fun release() {
        close()
        scope.cancel()
    }
}
