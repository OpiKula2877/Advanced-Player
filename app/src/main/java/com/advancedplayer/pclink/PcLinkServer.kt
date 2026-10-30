package com.advancedplayer.pclink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.Uri
import android.util.Log
import com.advancedplayer.AppContainer
import com.advancedplayer.data.LyricsLoader
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Serves the library to the PC over Bluetooth (RFCOMM), one client at a time.
 * The commands are described in [PcLinkProtocol] and in desktop/README.md.
 * Nothing is sent unless the user turned the PC link on, and the phone never connects anywhere itself.
 */
class PcLinkServer(private val context: Context, private val container: AppContainer) {
    @Volatile
    private var running = false
    private var serverSocket: BluetoothServerSocket? = null

    /** Opens the Bluetooth server. Returns false when Bluetooth is off or the server cannot be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false

        val socket = try {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, PcLinkProtocol.SERVICE_UUID)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open the Bluetooth server", e)
            return false
        }

        serverSocket = socket
        running = true
        thread(name = "pc-link", isDaemon = true) {
            while (running) {
                val client: BluetoothSocket = try {
                    socket.accept()
                } catch (e: Exception) {
                    break // stop() closed the server socket
                }

                try {
                    client.use { serve(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "The PC session ended with an error", e)
                }
            }
        }
        return true
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Closing the server socket failed", e)
        }
        serverSocket = null
    }

    private fun serve(socket: BluetoothSocket) {
        val input = socket.inputStream.buffered()
        val output = socket.outputStream
        while (true) {
            val line = PcLinkProtocol.readLine(input) ?: return
            val parts = line.trim().split(' ')
            when (parts[0]) {
                "HELLO" -> output.write("OK advancedplayer ${PcLinkProtocol.VERSION} ${trackCount()}\n".toByteArray(Charsets.US_ASCII))
                "LIST" -> PcLinkProtocol.writeBlock(output, listJson().toByteArray(Charsets.UTF_8))
                "GET" -> sendFile(parts.getOrNull(1)?.toLongOrNull(), output)
                "ART" -> sendArt(parts.getOrNull(1)?.toLongOrNull(), output)
                "LRC" -> sendLyrics(parts.getOrNull(1)?.toLongOrNull(), output)
                "BYE" -> return
                else -> output.write("ERR unknown command\n".toByteArray(Charsets.US_ASCII))
            }
            output.flush()
        }
    }

    private fun trackCount(): Int = runBlocking { container.db.tracks().all().size }

    /** The songs as a JSON array. Video files are not shared. */
    private fun listJson(): String {
        val tracks = runBlocking { container.db.tracks().all() }.filter { !it.isVideo }
        val array = JSONArray()
        tracks.forEach { t ->
            array.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("genre", t.genre)
                    .put("year", t.year)
                    .put("trackNo", t.trackNumber)
                    .put("durationMs", t.durationMs)
                    .put("size", t.sizeBytes)
                    .put("name", t.filePath?.substringAfterLast('/') ?: t.title)
                    .put("mime", t.mimeType)
                    .put("dateAdded", t.dateAdded),
            )
        }
        return array.toString()
    }

    private fun sendFile(id: Long?, output: OutputStream) {
        val track = id?.let { runBlocking { container.db.tracks().byId(it) } }
        val bytes = track?.let {
            try {
                context.contentResolver.openInputStream(Uri.parse(it.uri))?.use { stream -> stream.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read ${it.uri}", e)
                null
            }
        }
        if (bytes == null) output.write("-1\n".toByteArray(Charsets.US_ASCII))
        else PcLinkProtocol.writeBlock(output, bytes)
    }

    private fun sendArt(id: Long?, output: OutputStream) {
        val file = id?.let { runBlocking { container.art.artFile(it) } }
        val bytes = file?.takeIf { it.exists() }?.readBytes()
        if (bytes == null) output.write("0\n".toByteArray(Charsets.US_ASCII))
        else PcLinkProtocol.writeBlock(output, bytes)
    }

    private fun sendLyrics(id: Long?, output: OutputStream) {
        val track = id?.let { runBlocking { container.db.tracks().byId(it) } }
        val text = track?.let { LyricsLoader.load(context, it) }?.let { PcLinkProtocol.toLrc(it) }
        if (text.isNullOrEmpty()) output.write("0\n".toByteArray(Charsets.US_ASCII))
        else PcLinkProtocol.writeBlock(output, text.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val TAG = "PcLinkServer"
        private const val SERVICE_NAME = "Advanced Player"
    }
}
