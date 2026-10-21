package com.hasanhoca.canliders.meeting;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SdpObserver;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** En fazla birkaç öğrencilik ders için öğretmen merkezli WebRTC bağlantı yöneticisi. */
public final class MeshMeetingClient implements SignalingClient.Listener, AutoCloseable {
    public interface Listener {
        void onReady();
        void onRemoteVideo(String participantId, VideoTrack track);
        void onParticipantLeft(String participantId);
        void onError(String message);
    }

    private final PeerConnectionFactory factory;
    private final boolean teacher;
    private final VideoTrack localVideo;
    private final AudioTrack localAudio;
    private final Listener listener;
    private final Map<String, PeerConnection> peers = new HashMap<>();
    private SignalingClient signaling;

    public MeshMeetingClient(PeerConnectionFactory factory, boolean teacher,
            VideoTrack localVideo, AudioTrack localAudio, Listener listener) {
        this.factory = factory;
        this.teacher = teacher;
        this.localVideo = localVideo;
        this.localAudio = localAudio;
        this.listener = listener;
    }

    public void connect(String serverUrl, String roomCode, String participantId) {
        signaling = new SignalingClient(serverUrl, roomCode, participantId, this);
    }

    @Override
    public void onConnected() {
        listener.onReady();
    }

    @Override
    public void onSignal(String senderId, String type, JSONObject payload) {
        if ("peers".equals(type) && teacher && payload != null) {
            org.json.JSONArray participants = payload.optJSONArray("participants");
            if (participants != null) {
                for (int index = 0; index < participants.length(); index++) {
                    createOffer(participants.optString(index));
                }
            }
        } else if ("joined".equals(type) && teacher && payload != null) {
            createOffer(payload.optString("participant"));
        } else if ("left".equals(type) && payload != null) {
            removePeer(payload.optString("participant"));
        } else if ("offer".equals(type) && payload != null) {
            acceptOffer(senderId, payload.optString("sdp"));
        } else if ("answer".equals(type) && payload != null) {
            PeerConnection peer = peers.get(senderId);
            if (peer != null) peer.setRemoteDescription(new SimpleSdpObserver(),
                    new SessionDescription(SessionDescription.Type.ANSWER,
                            payload.optString("sdp")));
        } else if ("ice".equals(type) && payload != null) {
            PeerConnection peer = peers.get(senderId);
            if (peer != null) peer.addIceCandidate(new IceCandidate(
                    payload.optString("mid"), payload.optInt("line"),
                    payload.optString("candidate")));
        }
    }

    @Override
    public void onDisconnected(String reason) {
        listener.onError(reason);
    }

    private PeerConnection createPeer(String participantId) {
        PeerConnection existing = peers.get(participantId);
        if (existing != null) return existing;
        List<PeerConnection.IceServer> iceServers = Collections.singletonList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(iceServers);
        PeerConnection peer = factory.createPeerConnection(configuration,
                new PeerObserver(participantId));
        if (peer == null) throw new IllegalStateException("Görüntülü bağlantı oluşturulamadı");
        if (localVideo != null) peer.addTrack(localVideo,
                Collections.singletonList("hasan-hoca-ders"));
        if (localAudio != null) peer.addTrack(localAudio,
                Collections.singletonList("hasan-hoca-ders"));
        peers.put(participantId, peer);
        return peer;
    }

    private void createOffer(String participantId) {
        if (participantId == null || participantId.isEmpty()) return;
        PeerConnection peer = createPeer(participantId);
        peer.createOffer(new LocalSdpObserver(participantId, "offer", peer),
                new org.webrtc.MediaConstraints());
    }

    private void acceptOffer(String participantId, String sdp) {
        PeerConnection peer = createPeer(participantId);
        peer.setRemoteDescription(new SimpleSdpObserver() {
            @Override public void onSetSuccess() {
                peer.createAnswer(new LocalSdpObserver(participantId, "answer", peer),
                        new org.webrtc.MediaConstraints());
            }
            @Override public void onSetFailure(String error) {
                listener.onError("Ders bağlantısı kabul edilemedi: " + error);
            }
        }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
    }

    private void removePeer(String participantId) {
        PeerConnection peer = peers.remove(participantId);
        if (peer != null) peer.dispose();
        listener.onParticipantLeft(participantId);
    }

    private final class LocalSdpObserver extends SimpleSdpObserver {
        private final String participantId;
        private final String signalType;
        private final PeerConnection peer;

        LocalSdpObserver(String participantId, String signalType, PeerConnection peer) {
            this.participantId = participantId;
            this.signalType = signalType;
            this.peer = peer;
        }

        @Override
        public void onCreateSuccess(SessionDescription description) {
            peer.setLocalDescription(new SimpleSdpObserver(), description);
            JSONObject payload = new JSONObject();
            try {
                payload.put("sdp", description.description);
                signaling.send(participantId, signalType, payload);
            } catch (JSONException error) {
                listener.onError("Bağlantı teklifi hazırlanamadı");
            }
        }
    }

    private final class PeerObserver implements PeerConnection.Observer {
        private final String participantId;
        PeerObserver(String participantId) { this.participantId = participantId; }

        @Override public void onIceCandidate(IceCandidate candidate) {
            JSONObject payload = new JSONObject();
            try {
                payload.put("mid", candidate.sdpMid);
                payload.put("line", candidate.sdpMLineIndex);
                payload.put("candidate", candidate.sdp);
                signaling.send(participantId, "ice", payload);
            } catch (JSONException error) {
                listener.onError("Bağlantı adayı gönderilemedi");
            }
        }
        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            MediaStreamTrack track = receiver.track();
            if (track instanceof VideoTrack) {
                listener.onRemoteVideo(participantId, (VideoTrack) track);
            }
        }
        @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
        @Override public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
        @Override public void onAddStream(MediaStream stream) {}
        @Override public void onRemoveStream(MediaStream stream) {}
        @Override public void onDataChannel(DataChannel channel) {}
        @Override public void onRenegotiationNeeded() {}
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription description) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }

    @Override
    public void close() {
        for (PeerConnection peer : new ArrayList<>(peers.values())) peer.dispose();
        peers.clear();
        if (signaling != null) signaling.close();
        signaling = null;
    }
}
