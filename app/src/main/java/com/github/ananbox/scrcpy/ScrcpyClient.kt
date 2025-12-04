package com.github.ananbox.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Scrcpy client for Android - connects to a scrcpy server and decodes H264 video.
 * 
 * This is a simplified implementation that:
 * 1. Connects to the scrcpy server via TCP socket
 * 2. Reads the video stream (H264 encoded)
 * 3. Decodes using Android's MediaCodec
 * 4. Renders to the provided Surface
 */
class ScrcpyClient(
    private val address: String,
    private val port: Int = 5555
) {
    companion object {
        private const val TAG = "ScrcpyClient"
        
        // Scrcpy protocol constants
        private const val DEVICE_NAME_FIELD_LENGTH = 64
        
        // Packet header flags
        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62
    }
    
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var decoder: MediaCodec? = null
    private var decoderThread: Thread? = null
    private var readerThread: Thread? = null
    
    @Volatile
    private var running = false
    
    @Volatile
    private var connected = false
    
    private var displayWidth = 0
    private var displayHeight = 0
    private var deviceName = ""
    
    // Callback for connection events
    var onConnected: ((deviceName: String, width: Int, height: Int) -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onError: ((error: String) -> Unit)? = null
    
    /**
     * Connect to the scrcpy server and start decoding video.
     * 
     * @param surface The Surface to render the decoded video to
     */
    fun connect(surface: Surface) {
        if (running) {
            Log.w(TAG, "Already running")
            return
        }
        
        running = true
        
        thread(start = true, name = "ScrcpyConnect") {
            try {
                Log.i(TAG, "Connecting to scrcpy server at $address:$port")
                
                socket = Socket(address, port)
                inputStream = socket!!.getInputStream()
                outputStream = socket!!.getOutputStream()
                
                Log.i(TAG, "Connected, reading metadata...")
                
                // Read metadata (device name + codec info)
                if (!readMetadata()) {
                    onError?.invoke("Failed to read metadata")
                    disconnect()
                    return@thread
                }
                
                Log.i(TAG, "Device: $deviceName, Display: ${displayWidth}x${displayHeight}")
                
                // Initialize decoder
                if (!initDecoder(surface)) {
                    onError?.invoke("Failed to initialize decoder")
                    disconnect()
                    return@thread
                }
                
                connected = true
                onConnected?.invoke(deviceName, displayWidth, displayHeight)
                
                // Start reading and decoding video packets
                startVideoStream()
                
            } catch (e: IOException) {
                Log.e(TAG, "Connection error", e)
                onError?.invoke("Connection failed: ${e.message}")
                disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                onError?.invoke("Error: ${e.message}")
                disconnect()
            }
        }
    }
    
    /**
     * Disconnect from the server and stop decoding.
     */
    fun disconnect() {
        running = false
        connected = false
        
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing decoder", e)
        }
        
        socket = null
        inputStream = null
        outputStream = null
        decoder = null
        
        onDisconnected?.invoke("Disconnected")
    }
    
    /**
     * Send a touch event to the server.
     */
    fun sendTouchEvent(x: Int, y: Int, action: Int, pointerId: Int) {
        if (!connected) return
        
        // Scrcpy control message format for touch events
        // Type (1 byte) + action (1 byte) + pointerId (8 bytes) + position (8 bytes) + width/height (4 bytes each) + pressure (2 bytes) + buttons (4 bytes)
        val msg = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
        msg.put(2) // SC_CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT
        msg.put(action.toByte())
        msg.putLong(pointerId.toLong())
        msg.putInt(x)
        msg.putInt(y)
        msg.putShort(displayWidth.toShort())
        msg.putShort(displayHeight.toShort())
        msg.putShort(0xFFFF.toShort()) // pressure (max)
        msg.putInt(0) // action button
        msg.putInt(1) // buttons (primary)
        
        try {
            outputStream?.write(msg.array())
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending touch event", e)
        }
    }
    
    /**
     * Send a key event to the server.
     */
    fun sendKeyEvent(keyCode: Int, action: Int, metaState: Int = 0) {
        if (!connected) return
        
        val msg = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN)
        msg.put(0) // SC_CONTROL_MSG_TYPE_INJECT_KEYCODE
        msg.put(action.toByte())
        msg.putInt(keyCode)
        msg.putInt(0) // repeat
        msg.putInt(metaState)
        
        try {
            outputStream?.write(msg.array())
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending key event", e)
        }
    }
    
    private fun readMetadata(): Boolean {
        val input = inputStream ?: return false
        
        // Read device name (64 bytes, null-terminated string)
        val nameBytes = ByteArray(DEVICE_NAME_FIELD_LENGTH)
        if (input.read(nameBytes) != DEVICE_NAME_FIELD_LENGTH) {
            Log.e(TAG, "Failed to read device name")
            return false
        }
        deviceName = String(nameBytes).trim('\u0000')
        
        // Read codec ID (4 bytes)
        val codecBytes = ByteArray(4)
        if (input.read(codecBytes) != 4) {
            Log.e(TAG, "Failed to read codec ID")
            return false
        }
        Log.i(TAG, "Codec ID: ${String(codecBytes)}")
        
        // Read initial video size (4 bytes width + 4 bytes height)
        val sizeBytes = ByteArray(8)
        if (input.read(sizeBytes) != 8) {
            Log.e(TAG, "Failed to read video size")
            return false
        }
        val sizeBuf = ByteBuffer.wrap(sizeBytes).order(ByteOrder.BIG_ENDIAN)
        displayWidth = sizeBuf.int
        displayHeight = sizeBuf.int
        
        return displayWidth > 0 && displayHeight > 0
    }
    
    private fun initDecoder(surface: Surface): Boolean {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displayWidth, displayHeight)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, displayWidth * displayHeight)
            
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            decoder?.configure(format, surface, null, 0)
            decoder?.start()
            
            Log.i(TAG, "Decoder initialized: ${displayWidth}x${displayHeight}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize decoder", e)
            return false
        }
    }
    
    private fun startVideoStream() {
        val input = inputStream ?: return
        val dec = decoder ?: return
        
        readerThread = thread(start = true, name = "ScrcpyReader") {
            val headerBuf = ByteArray(12) // pts (8 bytes) + size (4 bytes)
            
            while (running) {
                try {
                    // Read packet header
                    if (input.read(headerBuf) != 12) {
                        Log.w(TAG, "End of stream")
                        break
                    }
                    
                    val header = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN)
                    val pts = header.long
                    val size = header.int
                    
                    if (size <= 0 || size > 10 * 1024 * 1024) {
                        Log.e(TAG, "Invalid packet size: $size")
                        continue
                    }
                    
                    // Check flags
                    val isConfig = (pts and PACKET_FLAG_CONFIG) != 0L
                    val actualPts = pts and (PACKET_FLAG_CONFIG.inv() and PACKET_FLAG_KEY_FRAME.inv())
                    
                    // Read packet data
                    val data = ByteArray(size)
                    var read = 0
                    while (read < size) {
                        val n = input.read(data, read, size - read)
                        if (n < 0) {
                            Log.w(TAG, "End of stream while reading packet")
                            break
                        }
                        read += n
                    }
                    
                    if (read != size) {
                        Log.e(TAG, "Failed to read full packet: $read / $size")
                        continue
                    }
                    
                    // Feed to decoder
                    feedDecoder(data, actualPts, isConfig)
                    
                } catch (e: IOException) {
                    if (running) {
                        Log.e(TAG, "Read error", e)
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet", e)
                }
            }
            
            Log.i(TAG, "Reader thread exiting")
            if (running) {
                disconnect()
            }
        }
        
        // Start decoder output thread
        decoderThread = thread(start = true, name = "ScrcpyDecoder") {
            val info = MediaCodec.BufferInfo()
            
            while (running) {
                try {
                    val outputIndex = dec.dequeueOutputBuffer(info, 10000)
                    
                    when {
                        outputIndex >= 0 -> {
                            // Render the frame (releaseOutputBuffer with render=true)
                            dec.releaseOutputBuffer(outputIndex, true)
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = dec.outputFormat
                            Log.i(TAG, "Output format changed: $newFormat")
                        }
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output available yet
                        }
                    }
                } catch (e: IllegalStateException) {
                    if (running) {
                        Log.e(TAG, "Decoder error", e)
                    }
                    break
                }
            }
            
            Log.i(TAG, "Decoder thread exiting")
        }
    }
    
    private fun feedDecoder(data: ByteArray, pts: Long, isConfig: Boolean) {
        val dec = decoder ?: return
        
        try {
            val inputIndex = dec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data)
                
                val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                dec.queueInputBuffer(inputIndex, 0, data.size, pts, flags)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error feeding decoder", e)
        }
    }
    
    fun isConnected(): Boolean = connected
    
    fun getDisplayWidth(): Int = displayWidth
    
    fun getDisplayHeight(): Int = displayHeight
}
