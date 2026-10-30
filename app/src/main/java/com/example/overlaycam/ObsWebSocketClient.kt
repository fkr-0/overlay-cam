package com.example.overlaycam

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject

internal data class ObsEndpoint(
    val host: String,
    val port: Int = 4455,
    val password: String = "",
    val secure: Boolean = false,
)

internal enum class ObsConnectionState {
    Disconnected,
    Connecting,
    Authenticating,
    Ready,
    Error,
}

internal object ObsWebSocketAuth {
    fun computeAuthentication(password: String, salt: String, challenge: String): String {
        val secret = (password + salt).encodeUtf8().sha256().base64()
        return (secret + challenge).encodeUtf8().sha256().base64()
    }
}

internal class ObsWebSocketClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRequests = ConcurrentHashMap<String, (Boolean, String?) -> Unit>()
    private var socket: WebSocket? = null
    private var endpoint: ObsEndpoint? = null
    @Volatile
    private var identified = false
    private var listener: ((ObsConnectionState, String) -> Unit)? = null

    fun connect(endpoint: ObsEndpoint, listener: (ObsConnectionState, String) -> Unit) {
        disconnect()
        this.listener = listener
        this.endpoint = endpoint
        identified = false

        val host = endpoint.host.trim()
        if (host.isEmpty()) {
            notify(ObsConnectionState.Error, "Enter the OBS host")
            return
        }

        val scheme = if (endpoint.secure) "wss" else "ws"
        notify(ObsConnectionState.Connecting, "Connecting to OBS…")
        val request = Request.Builder()
            .url("$scheme://$host:${endpoint.port}")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                notify(ObsConnectionState.Authenticating, "OBS WebSocket opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                identified = false
                notify(ObsConnectionState.Error, t.message ?: "OBS connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                identified = false
                notify(ObsConnectionState.Disconnected, reason.ifBlank { "Disconnected" })
            }
        })
    }

    fun disconnect() {
        identified = false
        pendingRequests.clear()
        socket?.close(1000, "Overlay Cam disconnect")
        socket = null
        endpoint = null
    }

    fun setCurrentScene(sceneName: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        request(
            type = "SetCurrentProgramScene",
            data = JSONObject().put("sceneName", sceneName),
            onResult = onResult,
        )
    }

    fun startStream(onResult: (Boolean, String?) -> Unit = { _, _ -> }) =
        request("StartStream", onResult = onResult)

    fun stopStream(onResult: (Boolean, String?) -> Unit = { _, _ -> }) =
        request("StopStream", onResult = onResult)

    fun startVirtualCamera(onResult: (Boolean, String?) -> Unit = { _, _ -> }) =
        request("StartVirtualCam", onResult = onResult)

    fun stopVirtualCamera(onResult: (Boolean, String?) -> Unit = { _, _ -> }) =
        request("StopVirtualCam", onResult = onResult)

    private fun handleMessage(webSocket: WebSocket, text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optInt("op", -1)) {
            0 -> {
                val data = message.optJSONObject("d") ?: JSONObject()
                val authentication = data.optJSONObject("authentication")
                val identify = JSONObject().put("rpcVersion", 1)
                if (authentication != null) {
                    val configured = endpoint ?: return
                    identify.put(
                        "authentication",
                        ObsWebSocketAuth.computeAuthentication(
                            configured.password,
                            authentication.optString("salt"),
                            authentication.optString("challenge"),
                        ),
                    )
                }
                webSocket.send(JSONObject().put("op", 1).put("d", identify).toString())
                notify(ObsConnectionState.Authenticating, "Authenticating with OBS…")
            }

            2 -> {
                identified = true
                notify(ObsConnectionState.Ready, "OBS connected")
            }

            7 -> {
                val data = message.optJSONObject("d") ?: return
                val requestId = data.optString("requestId")
                val status = data.optJSONObject("requestStatus")
                val ok = status?.optBoolean("result", false) == true
                val comment = status?.optString("comment")?.takeIf(String::isNotBlank)
                pendingRequests.remove(requestId)?.invoke(ok, comment)
                if (!ok) {
                    // A rejected request must not drop a live session out of Ready.
                    notify(
                        if (identified) ObsConnectionState.Ready else ObsConnectionState.Error,
                        comment ?: "OBS request failed",
                    )
                }
            }
        }
    }

    private fun request(
        type: String,
        data: JSONObject = JSONObject(),
        onResult: (Boolean, String?) -> Unit,
    ) {
        val webSocket = socket
        if (webSocket == null || !identified) {
            onResult(false, "OBS is not connected")
            notify(ObsConnectionState.Error, "OBS is not connected")
            return
        }

        val requestId = UUID.randomUUID().toString()
        pendingRequests[requestId] = onResult
        val requestData = JSONObject()
            .put("requestType", type)
            .put("requestId", requestId)
            .put("requestData", data)
        webSocket.send(JSONObject().put("op", 6).put("d", requestData).toString())
    }

    private fun notify(state: ObsConnectionState, message: String) {
        mainHandler.post { listener?.invoke(state, message) }
    }
}
