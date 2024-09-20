package com.leesfamily.chuno.openvidu.game;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.leesfamily.chuno.game.game.GameViewFragment;
import com.leesfamily.chuno.openvidu.observers.CustomPeerConnectionObserver;
import com.leesfamily.chuno.openvidu.observers.CustomSdpObserver;
import com.leesfamily.chuno.openvidu.websocket.CustomWebSocketGame;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SessionGame {

    private LocalParticipantGame localParticipantGame;
    private Map<String, RemoteParticipantGame> remoteParticipants = new HashMap<>();
    private String id;
    private String token;

    private final List<IceServer> iceServersDefault =
            Arrays.asList(IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
    private List<IceServer> iceServers = new ArrayList();

    private ViewPager2 views_container;
    private PeerConnectionFactory peerConnectionFactory;
    private CustomWebSocketGame websocket;
    private GameViewFragment fragment;

    public SessionGame(String id, String token, ViewPager2 views_container, GameViewFragment fragment) {
        this.id = id;
        this.token = token;
        this.views_container = views_container;
        this.fragment = fragment;
        Log.d("추노", "SessionGame: fragment : " + this.fragment);
        // 세션 구성을 위해 PeerConnectionFactory를 초기화하고 빌드한다.
        // 이것은 Google WebRTC 라이브러리와 WebRTCPeer연결을 초기화하는 방법이다.
        // Creating a new PeerConnectionFactory instance
        PeerConnectionFactory.InitializationOptions.Builder optionsBuilder = PeerConnectionFactory.InitializationOptions.builder(fragment.getContext());
        optionsBuilder.setEnableInternalTracer(true);
        PeerConnectionFactory.InitializationOptions opt = optionsBuilder.createInitializationOptions();
        PeerConnectionFactory.initialize(opt);
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        // Using software encoder and decoder
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;
        encoderFactory = new SoftwareVideoEncoderFactory();
        decoderFactory = new SoftwareVideoDecoderFactory();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(options)
                .createPeerConnectionFactory();
    }

    public void setWebSocket(CustomWebSocketGame websocket) {
        this.websocket = websocket;
    }

    public PeerConnection createLocalPeerConnection() {
        PeerConnection.RTCConfiguration config =
                new PeerConnection.RTCConfiguration(iceServers.isEmpty()
                        ? iceServersDefault
                        : iceServers);
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
        config.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        config.keyType = PeerConnection.KeyType.ECDSA;
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(config, new CustomPeerConnectionObserver("local") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                websocket.onIceCandidate(iceCandidate, localParticipantGame.getConnectionId());
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                if (PeerConnection.SignalingState.STABLE.equals(signalingState)) {
                    // SDP Offer/Answer finished. Add stored remote candidates.
                    Iterator<IceCandidate> it = localParticipantGame.getIceCandidateList().iterator();
                    while (it.hasNext()) {
                        IceCandidate candidate = it.next();
                        localParticipantGame.getPeerConnection().addIceCandidate(candidate);
                        it.remove();
                    }
                }
            }
        });

        if (localParticipantGame.getAudioTrack() != null) {
            peerConnection.addTransceiver(localParticipantGame.getAudioTrack(),
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));
        }
        if (localParticipantGame.getVideoTrack() != null) {
            peerConnection.addTransceiver(localParticipantGame.getVideoTrack(),
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));
        }

        return peerConnection;
    }

    public void createRemotePeerConnection(final String connectionId) {
        PeerConnection.RTCConfiguration config =
                new PeerConnection.RTCConfiguration(iceServers.isEmpty()
                        ? iceServersDefault
                        : iceServers);
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
        config.continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        config.keyType = PeerConnection.KeyType.ECDSA;
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(config, new CustomPeerConnectionObserver("remotePeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                websocket.onIceCandidate(iceCandidate, connectionId);
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                super.onAddTrack(rtpReceiver, mediaStreams);
                fragment.setRemoteMediaStream(mediaStreams[0], remoteParticipants.get(connectionId));
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                if (PeerConnection.SignalingState.STABLE.equals(signalingState)) {
                    // SDP Offer/Answer finished. Add stored remote candidates.
                    final RemoteParticipantGame remoteParticipant = remoteParticipants.get(connectionId);
                    Iterator<IceCandidate> it = remoteParticipant.getIceCandidateList().iterator();
                    while (it.hasNext()) {
                        IceCandidate candidate = it.next();
                        remoteParticipant.getPeerConnection().addIceCandidate(candidate);
                        it.remove();
                    }
                }
            }
        });

        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));

        this.remoteParticipants.get(connectionId).setPeerConnection(peerConnection);
    }

    public void createOfferForPublishing(MediaConstraints constraints) {
        localParticipantGame.getPeerConnection().createOffer(new CustomSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                super.onCreateSuccess(sdp);
                Log.i("createOffer SUCCESS", sdp.toString());
                localParticipantGame.getPeerConnection().setLocalDescription(new CustomSdpObserver("createOffer_setLocalDescription") {
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        websocket.publishVideo(sdp);
                    }
                }, sdp);
            }
        }, constraints);
    }

    public void createAnswerForSubscribing(RemoteParticipantGame remoteParticipant, String streamId, MediaConstraints constraints) {
        remoteParticipant.getPeerConnection().createAnswer(new CustomSdpObserver("createAnswerSubscribing") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                super.onCreateSuccess(sdp);
                Log.i("createAnswer SUCCESS", sdp.toString());
                remoteParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("createAnswerSubscribing_setLocalDescription") {
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        websocket.receiveVideoFrom(sdp, remoteParticipant, streamId);
                    }
                }, sdp);
            }
        }, constraints);
    }

    public String getId() {
        return this.id;
    }

    public String getToken() {
        return this.token;
    }

    public void setIceServers(List<IceServer> iceServers) {
        this.iceServers = iceServers;
    }

    public LocalParticipantGame getLocalParticipant() {
        return this.localParticipantGame;
    }

    public void setLocalParticipant(LocalParticipantGame localParticipantGame) {
        this.localParticipantGame = localParticipantGame;
    }

    public RemoteParticipantGame getRemoteParticipant(String id) {
        return this.remoteParticipants.get(id);
    }

    public PeerConnectionFactory getPeerConnectionFactory() {
        return this.peerConnectionFactory;
    }

    public void addRemoteParticipant(RemoteParticipantGame remoteParticipant) {
        this.remoteParticipants.put(remoteParticipant.getConnectionId(), remoteParticipant);
    }

    public RemoteParticipantGame removeRemoteParticipant(String id) {
        return this.remoteParticipants.remove(id);
    }

    public void leaveSession() {
        AsyncTask.execute(() -> {
            websocket.setWebsocketCancelled(true);
            if (websocket != null) {
                websocket.leaveRoom();
                websocket.disconnect();
            }
            this.localParticipantGame.dispose();
        });
        fragment.requireActivity().runOnUiThread(() -> {
            for (RemoteParticipantGame remoteParticipant : remoteParticipants.values()) {
                if (remoteParticipant.getPeerConnection() != null) {
                    remoteParticipant.getPeerConnection().close();
                }
                views_container.removeView(remoteParticipant.getView());
            }
        });
        AsyncTask.execute(() -> {
            if (peerConnectionFactory != null) {
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
            }
        });
    }

    public void removeView(View view) {
        this.views_container.removeView(view);
    }

}
