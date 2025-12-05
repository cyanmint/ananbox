package com.github.ananbox

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Helper class for ADB operations.
 * Implements a subset of the ADB protocol to push files and execute commands
 * on the container via the ADB forwarder.
 */
class AdbHelper(
    private val host: String,
    private val port: Int
) {
    companion object {
        private const val TAG = "AdbHelper"
        
        // scrcpy-server version - must match the bundled asset in assets/scrcpy-server
        const val SCRCPY_VERSION = "3.3.3"
        
        // ADB protocol constants
        private const val A_SYNC = 0x434e5953  // "SYNC"
        private const val A_CNXN = 0x4e584e43  // "CNXN"
        private const val A_OPEN = 0x4e45504f  // "OPEN"
        private const val A_OKAY = 0x59414b4f  // "OKAY"
        private const val A_CLSE = 0x45534c43  // "CLSE"
        private const val A_WRTE = 0x45545257  // "WRTE"
        
        private const val A_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 256 * 1024
        
        // SYNC commands
        private const val ID_STAT = 0x54415453  // "STAT"
        private const val ID_SEND = 0x444e4553  // "SEND"
        private const val ID_DATA = 0x41544144  // "DATA"
        private const val ID_DONE = 0x454e4f44  // "DONE"
        private const val ID_OKAY = 0x59414b4f  // "OKAY"
        private const val ID_FAIL = 0x4c494146  // "FAIL"
        
        private const val SCRCPY_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
        
        // Timeout for waiting for scrcpy server to start
        private const val SCRCPY_START_TIMEOUT_MS = 5000L
        private const val SCRCPY_POLL_INTERVAL_MS = 100L
    }
    
    private var socket: Socket? = null
    private var dataIn: DataInputStream? = null
    private var dataOut: DataOutputStream? = null
    private var localId = 1
    private var connected = false
    
    /**
     * Connect to the ADB server.
     */
    fun connect(): Boolean {
        try {
            Log.i(TAG, "Connecting to ADB at $host:$port")
            socket = Socket(host, port)
            socket?.soTimeout = 30000
            dataIn = DataInputStream(socket!!.getInputStream())
            dataOut = DataOutputStream(socket!!.getOutputStream())
            
            // Send CNXN message
            val systemIdentity = "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,fixed_push_symlink_timestamp,abb_exec,remount_shell,track_app,sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,sendrecv_v2_dry_run_send,openscreen_mdns"
            val identityBytes = systemIdentity.toByteArray(Charsets.UTF_8)
            
            sendMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, identityBytes)
            
            // Wait for CNXN response
            val response = readMessage()
            if (response.command == A_CNXN) {
                connected = true
                Log.i(TAG, "Connected to ADB")
                return true
            } else {
                Log.e(TAG, "Unexpected response: ${response.command}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ADB", e)
            return false
        }
    }
    
    /**
     * Disconnect from the ADB server.
     */
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        socket = null
        dataIn = null
        dataOut = null
        connected = false
    }
    
    /**
     * Push a file to the device.
     */
    fun pushFile(localFile: File, remotePath: String): Boolean {
        if (!connected) {
            Log.e(TAG, "Not connected")
            return false
        }
        
        try {
            Log.i(TAG, "Pushing ${localFile.absolutePath} to $remotePath")
            
            // Open sync connection
            val syncDest = "sync:"
            val myLocalId = localId++
            sendMessage(A_OPEN, myLocalId, 0, syncDest.toByteArray(Charsets.UTF_8))
            
            val openResponse = readMessage()
            if (openResponse.command != A_OKAY) {
                Log.e(TAG, "Failed to open sync connection")
                return false
            }
            val remoteId = openResponse.arg0
            
            // Send SEND command with path and mode
            val pathWithMode = "$remotePath,33261"  // 33261 = 0100755 (regular file, rwxr-xr-x)
            val sendCmd = ByteBuffer.allocate(8 + pathWithMode.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ID_SEND)
                .putInt(pathWithMode.length)
                .put(pathWithMode.toByteArray(Charsets.UTF_8))
                .array()
            
            sendMessage(A_WRTE, myLocalId, remoteId, sendCmd)
            waitForOkay(myLocalId)
            
            // Send file data in chunks
            FileInputStream(localFile).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val dataCmd = ByteBuffer.allocate(8 + bytesRead)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(ID_DATA)
                        .putInt(bytesRead)
                        .put(buffer, 0, bytesRead)
                        .array()
                    
                    sendMessage(A_WRTE, myLocalId, remoteId, dataCmd)
                    waitForOkay(myLocalId)
                }
            }
            
            // Send DONE with mtime
            val mtime = (localFile.lastModified() / 1000).toInt()
            val doneCmd = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ID_DONE)
                .putInt(mtime)
                .array()
            
            sendMessage(A_WRTE, myLocalId, remoteId, doneCmd)
            waitForOkay(myLocalId)
            
            // Read OKAY or FAIL response
            val result = readSyncResponse(myLocalId, remoteId)
            
            // Close the sync connection
            sendMessage(A_CLSE, myLocalId, remoteId, ByteArray(0))
            
            Log.i(TAG, "Push completed: $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push file", e)
            return false
        }
    }
    
    /**
     * Execute a shell command on the device.
     */
    fun shell(command: String, callback: ((String) -> Unit)? = null): Boolean {
        if (!connected) {
            Log.e(TAG, "Not connected")
            return false
        }
        
        try {
            Log.i(TAG, "Executing shell: $command")
            
            val shellCmd = "shell:$command"
            val myLocalId = localId++
            sendMessage(A_OPEN, myLocalId, 0, shellCmd.toByteArray(Charsets.UTF_8))
            
            val openResponse = readMessage()
            if (openResponse.command != A_OKAY) {
                Log.e(TAG, "Failed to open shell connection")
                return false
            }
            val remoteId = openResponse.arg0
            
            // Read output
            val output = StringBuilder()
            while (true) {
                val msg = readMessage()
                when (msg.command) {
                    A_WRTE -> {
                        val data = String(msg.data, Charsets.UTF_8)
                        output.append(data)
                        callback?.invoke(data)
                        sendMessage(A_OKAY, myLocalId, remoteId, ByteArray(0))
                    }
                    A_CLSE -> {
                        Log.i(TAG, "Shell closed")
                        break
                    }
                    else -> {
                        Log.w(TAG, "Unexpected message: ${msg.command}")
                    }
                }
            }
            
            Log.i(TAG, "Shell output: $output")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command", e)
            return false
        }
    }
    
    /**
     * Push scrcpy-server and start it.
     * Uses proper synchronization to wait for the server to start.
     */
    fun pushAndStartScrcpyServer(scrcpyServerFile: File, tunnelForward: Boolean = true): Boolean {
        try {
            // First push the scrcpy-server
            if (!pushFile(scrcpyServerFile, SCRCPY_SERVER_PATH)) {
                Log.e(TAG, "Failed to push scrcpy-server")
                return false
            }
            
            // Use CountDownLatch to wait for server startup
            val serverStartedLatch = CountDownLatch(1)
            
            // Start scrcpy server
            // The server needs to be started with app_process
            val scrcpyCmd = buildString {
                append("CLASSPATH=$SCRCPY_SERVER_PATH ")
                append("app_process / com.genymobile.scrcpy.Server $SCRCPY_VERSION ")
                append("tunnel_forward=${if (tunnelForward) "true" else "false"} ")
                append("audio=false ")
                append("control=true ")
                append("cleanup=true ")
                append("raw_stream=true")
            }
            
            // Run in background and watch for startup indication
            thread {
                shell(scrcpyCmd) { output ->
                    Log.d(TAG, "scrcpy: $output")
                    // scrcpy server outputs "[server]" when it starts listening
                    if (output.contains("[server]") || output.contains("Device:") || 
                        output.contains("listening") || output.contains("INFO:")) {
                        serverStartedLatch.countDown()
                    }
                }
            }
            
            // Wait for server to start with timeout
            val started = serverStartedLatch.await(SCRCPY_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            
            if (!started) {
                // Timeout - server may still be starting, give it a minimal wait
                Log.w(TAG, "Timeout waiting for scrcpy server startup signal, proceeding anyway")
                Thread.sleep(SCRCPY_POLL_INTERVAL_MS)
            }
            
            Log.i(TAG, "scrcpy server started: $started")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scrcpy server", e)
            return false
        }
    }
    
    private fun sendMessage(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(data.size)
            .putInt(checksum(data))
            .putInt(command.inv())
            .array()
        
        dataOut?.write(header)
        if (data.isNotEmpty()) {
            dataOut?.write(data)
        }
        dataOut?.flush()
    }
    
    private fun readMessage(): AdbMessage {
        val header = ByteArray(24)
        dataIn?.readFully(header)
        
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLength = buf.int
        val dataCheck = buf.int
        val magic = buf.int
        
        val data = if (dataLength > 0) {
            val d = ByteArray(dataLength)
            dataIn?.readFully(d)
            d
        } else {
            ByteArray(0)
        }
        
        return AdbMessage(command, arg0, arg1, data)
    }
    
    private fun waitForOkay(localId: Int) {
        val msg = readMessage()
        if (msg.command != A_OKAY) {
            throw Exception("Expected OKAY, got ${msg.command}")
        }
    }
    
    private fun readSyncResponse(localId: Int, remoteId: Int): Boolean {
        val msg = readMessage()
        if (msg.command != A_WRTE) {
            return false
        }
        
        // Send OKAY
        sendMessage(A_OKAY, localId, remoteId, ByteArray(0))
        
        val buf = ByteBuffer.wrap(msg.data).order(ByteOrder.LITTLE_ENDIAN)
        val id = buf.int
        
        return id == ID_OKAY
    }
    
    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        return sum
    }
    
    private data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray
    )
}
