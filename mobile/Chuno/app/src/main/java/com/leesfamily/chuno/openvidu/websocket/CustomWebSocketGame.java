package com.leesfamily.chuno.openvidu.websocket;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.leesfamily.chuno.game.game.GameViewFragment;
import com.leesfamily.chuno.openvidu.constants.JsonConstants;
import com.leesfamily.chuno.openvidu.game.LocalParticipantGame;
import com.leesfamily.chuno.openvidu.game.ParticipantGame;
import com.leesfamily.chuno.openvidu.game.RemoteParticipantGame;
import com.leesfamily.chuno.openvidu.game.SessionGame;
import com.leesfamily.chuno.openvidu.observers.CustomSdpObserver;
import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.SessionDescription;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class CustomWebSocketGame extends AsyncTask<GameViewFragment, Void, Void> implements WebSocketListener {

    private final String TAG = "CustomWebSocketListener";
    private final int PING_MESSAGE_INTERVAL = 5;
    private final TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            Log.i(TAG, ": authType: " + authType);
        }
    }};
    private AtomicInteger RPC_ID = new AtomicInteger(0);
    private AtomicInteger ID_PING = new AtomicInteger(-1);
    private AtomicInteger ID_JOINROOM = new AtomicInteger(-1);
    private AtomicInteger ID_LEAVEROOM = new AtomicInteger(-1);
    private AtomicInteger ID_PUBLISHVIDEO = new AtomicInteger(-1);
    private Map<Integer, Pair<String, String>> IDS_PREPARERECEIVEVIDEO = new ConcurrentHashMap<>();
    private Map<Integer, String> IDS_RECEIVEVIDEO = new ConcurrentHashMap<>();
    private Set<Integer> IDS_ONICECANDIDATE = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private SessionGame session;
    private String mediaServer;
    private GameViewFragment fragment;
    private WebSocket websocket;
    private boolean websocketCancelled = false;

    public CustomWebSocketGame(SessionGame session, GameViewFragment fragment) {
        this.session = session;
        this.fragment = fragment;
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        Log.i(TAG, "Text Message " + text);
        JSONObject json = new JSONObject(text);
        if (json.has(JsonConstants.RESULT)) {
            handleServerResponse(json);
        } else if (json.has(JsonConstants.ERROR)) {
            handleServerError(json);
        } else {
            handleServerEvent(json);
        }
    }

    private void handleServerResponse(JSONObject json) throws
            JSONException {
        final int rpcId = json.getInt(JsonConstants.ID);
        JSONObject result = new JSONObject(json.getString(JsonConstants.RESULT));

        if (result.has("value") && result.getString("value").equals("pong")) {
            // Response to ping
            Log.i(TAG, "pong");

        } else if (rpcId == this.ID_JOINROOM.get()) {
            // Response to joinRoom
//            fragment.viewToConnectedState();

            final LocalParticipantGame localParticipant = this.session.getLocalParticipant();
            final String localConnectionId = result.getString(JsonConstants.ID);
            localParticipant.setConnectionId(localConnectionId);

            this.mediaServer = result.getString(JsonConstants.MEDIA_SERVER);

            if (result.has(JsonConstants.ICE_SERVERS)) {
                final JSONArray jsonIceServers = result.getJSONArray(JsonConstants.ICE_SERVERS);
                List<IceServer> iceServers = new ArrayList();

                for (int i = 0; i < jsonIceServers.length(); i++) {
                    JSONObject jsonIceServer = jsonIceServers.getJSONObject(i);
                    List<String> urls = new ArrayList();
                    if (jsonIceServer.has("urls")) {
                        final JSONArray jsonUrls = jsonIceServer.getJSONArray("urls");
                        for (int j = 0; j < jsonUrls.length(); j++) {
                            urls.add(jsonUrls.getString(j));
                        }
                    }
                    if (jsonIceServer.has("url")) {
                        urls.add(jsonIceServer.getString("url"));
                    }

                    IceServer.Builder iceServerBuilder;
                    try {
                        iceServerBuilder = IceServer.builder(urls);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    if (jsonIceServer.has("username")) {
                        iceServerBuilder.setUsername(jsonIceServer.getString("username"));
                    }
                    if (jsonIceServer.has("credential")) {
                        iceServerBuilder.setPassword(jsonIceServer.getString("credential"));
                    }
                    iceServers.add(iceServerBuilder.createIceServer());
                }

                session.setIceServers(iceServers);
            }

            PeerConnection localPeerConnection = session.createLocalPeerConnection();

            localParticipant.setPeerConnection(localPeerConnection);

            MediaConstraints sdpConstraints = new MediaConstraints();
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "false"));
            sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"));
            session.createOfferForPublishing(sdpConstraints);

            if (result.getJSONArray(JsonConstants.VALUE).length() > 0) {
                // There were users already connected to the session
                addRemoteParticipantsAlreadyInRoom(result);
            }

        } else if (rpcId == this.ID_LEAVEROOM.get()) {
            // Response to leaveRoom
            if (websocket.isOpen()) {
                websocket.disconnect();
            }
        } else if (rpcId == this.ID_PUBLISHVIDEO.get()) {
            // Response to publishVideo
            LocalParticipantGame localParticipant = this.session.getLocalParticipant();
            SessionDescription remoteSdpAnswer = new SessionDescription(SessionDescription.Type.ANSWER, result.getString("sdpAnswer"));
            localParticipant.getPeerConnection().setRemoteDescription(new CustomSdpObserver("publishVideo_setRemoteDescription"), remoteSdpAnswer);
        } else if (this.IDS_PREPARERECEIVEVIDEO.containsKey(rpcId)) {
            // Response to prepareReceiveVideoFrom
            Pair<String, String> participantAndStream = IDS_PREPARERECEIVEVIDEO.remove(rpcId);
            RemoteParticipantGame remoteParticipant = session.getRemoteParticipant(participantAndStream.first);
            String streamId = participantAndStream.second;
            SessionDescription remoteSdpOffer = new SessionDescription(SessionDescription.Type.OFFER, result.getString("sdpOffer"));
            remoteParticipant.getPeerConnection().setRemoteDescription(new CustomSdpObserver("prepareReceiveVideoFrom_setRemoteDescription") {
                @Override
                public void onSetSuccess() {
                    super.onSetSuccess();
                    subscriptionInitiatedFromServer(remoteParticipant, streamId);
                }
            }, remoteSdpOffer);
        } else if (this.IDS_RECEIVEVIDEO.containsKey(rpcId)) {
            // Response to receiveVideoFrom
            String id = IDS_RECEIVEVIDEO.remove(rpcId);
            if ("kurento".equals(this.mediaServer)) {
                SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, result.getString("sdpAnswer"));
                session.getRemoteParticipant(id).getPeerConnection().setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);
            }
        } else if (this.IDS_ONICECANDIDATE.contains(rpcId)) {
            // Response to onIceCandidate
            IDS_ONICECANDIDATE.remove(rpcId);
        } else {
            Log.e(TAG, "Unrecognized server response: " + result);
        }
    }

    private void handleServerError(JSONObject json) throws JSONException {
        final JSONObject error = new JSONObject(json.getString(JsonConstants.ERROR));

        final int errorCode = error.getInt("code");
        final String errorMessage = error.getString("message");

        Log.e(TAG, "Server error code " + errorCode + ": " + errorMessage);
    }

    // 세션에 참여하기
    // WebSocket 연결이 설정되면 세션에 참여해야함
    // 다음 매개 변수와 함께 JSON-RPC매서드를 보내면 'joinRoom' 세션에 연결할 수 있다.
    // 응답으로 세션의 모든 기촌 참가자와 게시된 모든 스트림이 포함된 개체를 받게 됨
    // 먼저 이전 섹션의 OpenVidu 이벤트 청취에서 이벤트 참가자 가입 및 게시에 설명된 대로 처리합니다.
    // 또한 로컬 PeerConnection 및 MediaStream을 초기화하고 publishVideo RPC 메서드를 호출하여 자체 카메라를 게시해야 합니다(다음 지점 참조).

    public void joinRoom() {
        Map<String, String> joinRoomParams = new HashMap<>();
        joinRoomParams.put(JsonConstants.METADATA, "{\"clientData\": \"" + this.session.getLocalParticipant().getParticipantName() + "\"}");
        joinRoomParams.put("secret", "");
        joinRoomParams.put("session", this.session.getId());
        joinRoomParams.put("platform", "Android " + android.os.Build.VERSION.SDK_INT);
        joinRoomParams.put("token", this.session.getToken());
        joinRoomParams.put("sdkVersion", "2.22.0");
        this.ID_JOINROOM.set(this.sendJson(JsonConstants.JOINROOM_METHOD, joinRoomParams));
    }
    // leaveRoom방법 으로 세션 나가기
    // WebSocket을 통해 JSON-RPC를 보내야 합니다(이 경우 빈 매개변수).
    public void leaveRoom() {
        this.ID_LEAVEROOM.set(this.sendJson(JsonConstants.LEAVEROOM_METHOD));
    }

    // publishVideo메서드로 카메라 게시하기
    // 필수 매개변수와 함계 WebSocket을 통해 JSON-RPC메시지를 보내야한다.
    public void publishVideo(SessionDescription sessionDescription) {
        Map<String, String> publishVideoParams = new HashMap<>();
        publishVideoParams.put("audioActive", "true");
        publishVideoParams.put("videoActive", "true");
        publishVideoParams.put("doLoopback", "false");
        publishVideoParams.put("frameRate", "30");
        publishVideoParams.put("hasAudio", "true");
        publishVideoParams.put("hasVideo", "true");
        publishVideoParams.put("typeOfVideo", "CAMERA");
        publishVideoParams.put("videoDimensions", "{\"width\":320, \"height\":240}");
        publishVideoParams.put("sdpOffer", sessionDescription.description);
        this.ID_PUBLISHVIDEO.set(this.sendJson(JsonConstants.PUBLISHVIDEO_METHOD, publishVideoParams));
    }

    public void prepareReceiveVideoFrom(RemoteParticipantGame remoteParticipant, String streamId) {
        Map<String, String> prepareReceiveVideoFromParams = new HashMap<>();
        prepareReceiveVideoFromParams.put("sender", streamId);
        prepareReceiveVideoFromParams.put("reconnect", "false");
        this.IDS_PREPARERECEIVEVIDEO.put(this.sendJson(JsonConstants.PREPARERECEIVEVIDEO_METHOD, prepareReceiveVideoFromParams), new Pair<>(remoteParticipant.getConnectionId(), streamId));
    }

    // receiveVideo방법 으로 원격 비디오 구독하기
    // 아래와 같이 필수 매개변수와 함께 WebSocket을 통해 JSON-RPC를 보내야 합니다.
    public void receiveVideoFrom(SessionDescription sessionDescription, RemoteParticipantGame remoteParticipant, String streamId) {
        Map<String, String> receiveVideoFromParams = new HashMap<>();
        receiveVideoFromParams.put("sender", streamId);
        if ("kurento".equals(this.mediaServer)) {
            receiveVideoFromParams.put("sdpOffer", sessionDescription.description);
        } else {
            receiveVideoFromParams.put("sdpAnswer", sessionDescription.description);
        }
        this.IDS_RECEIVEVIDEO.put(this.sendJson(JsonConstants.RECEIVEVIDEO_METHOD, receiveVideoFromParams), remoteParticipant.getConnectionId());
    }

    public void onIceCandidate(IceCandidate iceCandidate, String endpointName) {
        Map<String, String> onIceCandidateParams = new HashMap<>();
        if (endpointName != null) {
            onIceCandidateParams.put("endpointName", endpointName);
        }
        onIceCandidateParams.put("candidate", iceCandidate.sdp);
        onIceCandidateParams.put("sdpMid", iceCandidate.sdpMid);
        onIceCandidateParams.put("sdpMLineIndex", Integer.toString(iceCandidate.sdpMLineIndex));
        this.IDS_ONICECANDIDATE.add(this.sendJson(JsonConstants.ONICECANDIDATE_METHOD, onIceCandidateParams));
    }

/*
     * OpenVidu에서 받은 이벤트 메시지를 처리하는 방법 구현
     * ice candidates가 언제 도착하는지, 새 사용자가 세션에 참여했는지, 사용자가 세션에 비디오를 게시했는지
     * 또는 일부 참가자가 세션을 나갔는지 알기위해 필수적
     *
     * iceCandidate: 이 이벤트는 OpenVidu에서 생성된 새로운 iceCandidate를 가져옵니다.
     *              적절한 PeerConnection 개체에 포함해야 합니다(로컬 PeerConnection 및 각 원격 PeerConnection에 대한 ICE 후보를 받습니다).
     *              타이밍 문제를 피하기 위해 응용 프로그램은 해당 PeerConnection 상태가 될 때까지 수신된 ICE 후보를 저장합니다 STABLE.
     *              도달할 때마다 한 번에 모두 처리합니다.
     * participantJoined: 이 이벤트는 새로운 참가자가 세션에 참여했음을 알려줍니다.
     *              새 PeerConnection 개체(새 사용자의 카메라 스트림을 수신할 수 있도록)와 UI의 새 비디오 요소를 초기화합니다.
     * participantPublished: 이 이벤트는 사용자가 세션에 비디오를 보내기 시작했음을 알려줍니다.
     *              적절하고 이미 초기화된 PeerConnection 개체를 통해 새 비디오 스트림을 수신하기 위해 ICE 협상을 시작해야 합니다.
     *              우리는 단순히 WebRTC 프로토콜을 따라 그렇게 합니다: 로컬 SDP 제공을 생성 및 설정하고,
     *              RPC 방법으로 OpenVidu로 전송하고, receiveVideoFrom이 PeerConnection의 원격 SDP 설명으로 수신된 응답을 설정합니다.
     * participantLeftEvent: 일부 사용자가 세션을 나갔을 때 전달됩니다.
     *              적절한 PeerConnection을 폐기하고 뷰를 업데이트하기만 하면 됩니다.
*/



    private void handleServerEvent(JSONObject json) throws JSONException {
        if (!json.has(JsonConstants.METHOD)) {
            Log.e(TAG, "Server event lacks a field '" + JsonConstants.METHOD + "'; JSON: "
                    + json.toString());
            return;
        }
        final String method = json.getString(JsonConstants.METHOD);

        if (!json.has(JsonConstants.PARAMS)) {
            Log.e(TAG, "Server event '" + method + "' lacks a field '" + JsonConstants.PARAMS
                    + "'; JSON: " + json.toString());
            return;
        }
        final JSONObject params = new JSONObject(json.getString(JsonConstants.PARAMS));

        switch (method) {
            case JsonConstants.ICE_CANDIDATE:
                iceCandidateEvent(params);
                break;
            case JsonConstants.PARTICIPANT_JOINED:
                participantJoinedEvent(params);
                break;
            case JsonConstants.PARTICIPANT_PUBLISHED:
                participantPublishedEvent(params);
                break;
            case JsonConstants.PARTICIPANT_LEFT:
                participantLeftEvent(params);
                break;
            default:
                throw new JSONException("Unknown server event '" + method + "'");
        }
    }

    public int sendJson(String method) {
        return this.sendJson(method, new HashMap<>());
    }

    public synchronized int sendJson(String method, Map<String, String> params) {
        final int id = RPC_ID.get();
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject paramsJson = new JSONObject();
            for (Map.Entry<String, String> param : params.entrySet()) {
                paramsJson.put(param.getKey(), param.getValue());
            }
            jsonObject.put("jsonrpc", JsonConstants.JSON_RPCVERSION);
            jsonObject.put("method", method);
            jsonObject.put("id", id);
            jsonObject.put("params", paramsJson);
        } catch (JSONException e) {
            Log.e(TAG, "JSONException raised on sendJson", e);
            return -1;
        }
        this.websocket.sendText(jsonObject.toString());
        RPC_ID.incrementAndGet();
        return id;
    }

    private void addRemoteParticipantsAlreadyInRoom(JSONObject result) throws
            JSONException {
        for (int i = 0; i < result.getJSONArray(JsonConstants.VALUE).length(); i++) {
            JSONObject participantJson = result.getJSONArray(JsonConstants.VALUE).getJSONObject(i);
            RemoteParticipantGame remoteParticipant = this.newRemoteParticipantAux(participantJson);
            try {
                JSONArray streams = participantJson.getJSONArray("streams");
                for (int j = 0; j < streams.length(); j++) {
                    JSONObject stream = streams.getJSONObject(0);
                    String streamId = stream.getString("id");
                    this.subscribe(remoteParticipant, streamId);
                }
            } catch (Exception e) {
                //Sometimes when we enter in room the other participants have no stream
                //We catch that in this way the iteration of participants doesn't stop
                Log.e(TAG, "Error in addRemoteParticipantsAlreadyInRoom: " + e.getLocalizedMessage());
            }
        }
    }

    private void iceCandidateEvent(JSONObject params) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(params.getString("sdpMid"), params.getInt("sdpMLineIndex"), params.getString("candidate"));
        final String connectionId = params.getString("senderConnectionId");
        boolean isRemote = !session.getLocalParticipant().getConnectionId().equals(connectionId);
        final ParticipantGame participant = isRemote ? session.getRemoteParticipant(connectionId) : session.getLocalParticipant();
        final PeerConnection pc = participant.getPeerConnection();

        switch (pc.signalingState()) {
            case CLOSED:
                Log.e("saveIceCandidate error", "PeerConnection object is closed");
                break;
            case STABLE:
                if (pc.getRemoteDescription() != null) {
                    participant.getPeerConnection().addIceCandidate(iceCandidate);
                } else {
                    participant.getIceCandidateList().add(iceCandidate);
                }
                break;
            default:
                participant.getIceCandidateList().add(iceCandidate);
        }
    }

    private void participantJoinedEvent(JSONObject params) throws JSONException {
        this.newRemoteParticipantAux(params);
    }

    private void participantPublishedEvent(JSONObject params) throws
            JSONException {
        String remoteParticipantId = params.getString(JsonConstants.ID);
        final RemoteParticipantGame remoteParticipant = this.session.getRemoteParticipant(remoteParticipantId);
        final String streamId = params.getJSONArray("streams").getJSONObject(0).getString("id");
        this.subscribe(remoteParticipant, streamId);
    }

    private void participantLeftEvent(JSONObject params) throws JSONException {
        final RemoteParticipantGame remoteParticipant = this.session.removeRemoteParticipant(params.getString("connectionId"));
        remoteParticipant.dispose();
        Handler mainHandler = new Handler(fragment.requireContext().getMainLooper());
        Runnable myRunnable = () -> session.removeView(remoteParticipant.getView());
        mainHandler.post(myRunnable);
    }

    private RemoteParticipantGame newRemoteParticipantAux(JSONObject participantJson) throws JSONException {
        final String connectionId = participantJson.getString(JsonConstants.ID);
        String participantName = "";
        if (participantJson.getString(JsonConstants.METADATA) != null) {
            String jsonStringified = participantJson.getString(JsonConstants.METADATA);
            try {
                JSONObject json = new JSONObject(jsonStringified);
                String clientData = json.getString("clientData");
                if (clientData != null) {
                    participantName = clientData;
                }
            } catch(JSONException e) {
                participantName = jsonStringified;
            }
        }
        final RemoteParticipantGame remoteParticipant = new RemoteParticipantGame(connectionId, participantName, this.session);
        fragment.createRemoteParticipantVideo(remoteParticipant);
        this.session.createRemotePeerConnection(remoteParticipant.getConnectionId());
        return remoteParticipant;
    }

    private void subscribe(RemoteParticipantGame remoteParticipant, String streamId) {
        if ("kurento".equals(this.mediaServer)) {
            this.subscriptionInitiatedFromClient(remoteParticipant, streamId);
        } else {
            this.prepareReceiveVideoFrom(remoteParticipant, streamId);
        }
    }

    private void subscriptionInitiatedFromClient(RemoteParticipantGame remoteParticipant, String streamId) {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        remoteParticipant.getPeerConnection().createOffer(new CustomSdpObserver("remote offer sdp") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                super.onCreateSuccess(sdp);
                remoteParticipant.getPeerConnection().setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc") {
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        receiveVideoFrom(sdp, remoteParticipant, streamId);
                    }
                }, sdp);
            }
        }, sdpConstraints);
    }

    private void subscriptionInitiatedFromServer(RemoteParticipantGame remoteParticipant, String streamId) {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
        this.session.createAnswerForSubscribing(remoteParticipant, streamId, sdpConstraints);
    }

    public void setWebsocketCancelled(boolean websocketCancelled) {
        this.websocketCancelled = websocketCancelled;
    }

    public void disconnect() {
        this.websocket.disconnect();
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        Log.i(TAG, "State changed: " + newState.name());
    }

    @Override
    public void onConnected(WebSocket ws, Map<String, List<String>> headers) throws
            Exception {
        Log.i(TAG, "Connected");
        pingMessageHandler();
        this.joinRoom();
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "Connect error: " + cause);
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame
            serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.e(TAG, "Disconnected " + serverCloseFrame.getCloseReason() + " " + clientCloseFrame.getCloseReason() + " " + closedByServer);
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame");
    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Continuation Frame");
    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Text Frame");
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Binary Frame");
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Close Frame");
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Ping Frame");
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Pong Frame");
    }

    @Override
    public void onTextMessage(WebSocket websocket, byte[] data) throws Exception {

    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        Log.i(TAG, "Binary Message");
    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Sending Frame");
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame sent");
    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame unsent");
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread created");
    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread started");
    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws
            Exception {
        Log.i(TAG, "Thread stopping");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "Error!");
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame
            frame) throws Exception {
        Log.e(TAG, "Frame error!");
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException
            cause, List<WebSocketFrame> frames) throws Exception {
        Log.e(TAG, "Message error! " + cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause,
                                            byte[] compressed) throws Exception {
        Log.e(TAG, "Message decompression error!");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws
            Exception {
        Log.e(TAG, "Text message error! " + cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws
            Exception {
        Log.e(TAG, "Send error! " + cause);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws
            Exception {
        Log.e(TAG, "Unexpected error! " + cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        Log.e(TAG, "Handle callback error! " + cause);
    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]>
            headers) throws Exception {
        Log.i(TAG, "Sending Handshake! Hello!");
    }

    private void pingMessageHandler() {
        long initialDelay = 0L;
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(() -> {
            Map<String, String> pingParams = new HashMap<>();
            if (ID_PING.get() == -1) {
                // First ping call
                pingParams.put("interval", "5000");
            }
            ID_PING.set(sendJson(JsonConstants.PING_METHOD, pingParams));
        }, initialDelay, PING_MESSAGE_INTERVAL, TimeUnit.SECONDS);
    }

    private String getWebSocketAddress() {
        String wsUri;
        try {
            URI url = new URI(this.session.getToken());
            if (url.getPort() > -1) {
                wsUri = url.getScheme() + "://" + url.getHost() + ":" + url.getPort() + "/openvidu";
            } else {
                wsUri = url.getScheme() + "://" + url.getHost() + "/openvidu";
            }
            return wsUri;
        } catch (URISyntaxException e) {
            Log.e(TAG, "Wrong URL", e);
            e.printStackTrace();
            return "";
        }
    }

    // WebSocket을 통해 OpenVidu 배포와 Android 앱 간의 연결 설정
    // 세션과 상호 작용할 수 있음
    // 비동기 작업으로 백그라운드에서 수행
    @Override
    protected Void doInBackground(GameViewFragment... rankFragments) {
        try {
            WebSocketFactory factory = new WebSocketFactory();

            //Returns a SSLContext object that implements the specified secure socket protocol
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            factory.setSSLContext(sslContext);

            // Set the flag which indicates whether the hostname in the server's certificate should be verified or not.
            factory.setVerifyHostname(false);

            // Connecting the websocket to OpenVidu URL
            websocket = factory.createSocket(getWebSocketAddress());
            websocket.addListener(this);
            websocket.connect();
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException | WebSocketException e) {
            Log.e("WebSocket error", e.getMessage());
            Handler mainHandler = new Handler(fragment.requireContext().getMainLooper());
            Runnable myRunnable = () -> {
                Toast toast = Toast.makeText(fragment.getContext(), e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
                fragment.leaveSession();
            };
            mainHandler.post(myRunnable);
            websocketCancelled = true;
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... progress) {
        Log.i(TAG, "PROGRESS " + Arrays.toString(progress));
    }

}
