package com.hasanhoca.canliders.meeting;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Oda üyeleri arasında yalnızca WebRTC teklif/yanıt ve ICE bilgisi taşır. */
public final class SignalingClient extends WebSocketListener {
    public interface Listener {
        void onConnected();
        void onSignal(String senderId, String type, JSONObject payload);
        void onDisconnected(String reason);
    }

    private final OkHttpClient httpClient = new OkHttpClient();
    private final String participantId;
    private final Listener listener;
    private WebSocket socket;

    public SignalingClient(String baseUrl, String roomCode, String participantId,
            Listener listener) {
        this.participantId = participantId;
        this.listener = listener;
        String url = baseUrl.replaceFirst("/$", "") + "/room/" + roomCode
                + "?participant=" + participantId;
        socket = httpClient.newWebSocket(new Request.Builder().url(url).build(), this);
    }

    public void send(String targetId, String type, JSONObject payload) {
        try {
            JSONObject message = new JSONObject();
            message.put("sender", participantId);
            message.put("target", targetId);
            message.put("type", type);
            message.put("payload", payload);
            if (socket != null) socket.send(message.toString());
        } catch (JSONException error) {
            listener.onDisconnected("Bağlantı mesajı hazırlanamadı");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        listener.onConnected();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            JSONObject message = new JSONObject(text);
            listener.onSignal(message.optString("sender"), message.optString("type"),
                    message.optJSONObject("payload"));
        } catch (JSONException ignored) {
            // Geçersiz veya odayla ilgisiz paketler toplantıyı düşürmez.
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable error, Response response) {
        listener.onDisconnected(error.getMessage() == null
                ? "Oda bağlantısı kesildi" : error.getMessage());
    }

    public void close() {
        if (socket != null) socket.close(1000, "Ders kapatıldı");
        socket = null;
        httpClient.dispatcher().executorService().shutdown();
    }
}
