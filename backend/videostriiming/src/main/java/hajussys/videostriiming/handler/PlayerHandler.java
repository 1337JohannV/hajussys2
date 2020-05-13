package hajussys.videostriiming.handler;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hajussys.videostriiming.models.Session;
import org.kurento.client.*;
import org.kurento.commons.exception.KurentoException;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol handler for video player through WebRTC.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author David Fernandez (dfernandezlop@gmail.com)
 * @author Ivan Gracia (igracia@kurento.org)
 * @since 6.1.1
 */
public class PlayerHandler extends TextWebSocketHandler {

    @Autowired
    private KurentoClient kurento;

    private final Logger log = LoggerFactory.getLogger(PlayerHandler.class);
    private final Gson gson = new GsonBuilder().create();
    private final ConcurrentHashMap<String, Session> users = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        String sessionId = session.getId();
        log.debug("Incoming message {} from sessionId", jsonMessage, sessionId);

        try {
            switch (jsonMessage.get("id").getAsString()) {
                case "start":
                    start(session, jsonMessage);
                    break;
                case "stop":
                    stop(sessionId);
                    break;
                case "pause":
                    pause(sessionId);
                    break;
                case "resume":
                    resume(session);
                    break;
                case "debugDot":
                    debugDot(session);
                    break;
                case "doSeek":
                    doSeek(session, jsonMessage);
                    break;
                case "getPosition":
                    getPosition(session);
                    break;
                case "onIceCandidate":
                    onIceCandidate(sessionId, jsonMessage);
                    break;
                default:
                    sendError(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
                    break;
            }
        } catch (Throwable t) {
            log.error("Exception handling message {} in sessionId {}", jsonMessage, sessionId, t);
            sendError(session, t.getMessage());
        }
    }

    private void start(final WebSocketSession session, JsonObject jsonMessage) {
        // 1. Media pipeline
        final Session user = new Session(session);
        MediaPipeline pipeline = kurento.createMediaPipeline();
        user.setMediaPipeline(pipeline);
        WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
        user.setWebRtcEndpoint(webRtcEndpoint);
        String videourl = jsonMessage.get("videourl").getAsString();
        final PlayerEndpoint playerEndpoint = new PlayerEndpoint.Builder(pipeline, videourl).build();
        user.setPlayerEndpoint(playerEndpoint);
        users.put(session.getId(), user);

        playerEndpoint.connect(webRtcEndpoint);

        // 2. WebRtcEndpoint
        // ICE candidates
        webRtcEndpoint.addIceCandidateFoundListener(event -> {
            JsonObject response = new JsonObject();
            response.addProperty("id", "iceCandidate");
            response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }
            } catch (IOException e) {
                log.debug(e.getMessage());
            }
        });

        // Continue the SDP Negotiation: Generate an SDP Answer
        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

        log.info("[Handler::start] SDP Offer from browser to KMS:\n{}", sdpOffer);
        log.info("[Handler::start] SDP Answer from KMS to browser:\n{}", sdpAnswer);

        JsonObject response = new JsonObject();
        response.addProperty("id", "startResponse");
        response.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(session, response.toString());

        webRtcEndpoint.addMediaStateChangedListener(event -> {

            if (event.getNewState() == MediaState.CONNECTED) {
                VideoInfo videoInfo = playerEndpoint.getVideoInfo();

                JsonObject response1 = new JsonObject();
                response1.addProperty("id", "videoInfo");
                response1.addProperty("isSeekable", videoInfo.getIsSeekable());
                response1.addProperty("initSeekable", videoInfo.getSeekableInit());
                response1.addProperty("endSeekable", videoInfo.getSeekableEnd());
                response1.addProperty("videoDuration", videoInfo.getDuration());
                sendMessage(session, response1.toString());
            }
        });

        webRtcEndpoint.gatherCandidates();

        // 3. PlayEndpoint
        playerEndpoint.addErrorListener(event -> {
            log.info("ErrorEvent: {}", event.getDescription());
            sendPlayEnd(session);
        });

        playerEndpoint.addEndOfStreamListener(event -> {
            log.info("EndOfStreamEvent: {}", event.getTimestamp());
            sendPlayEnd(session);
        });

        playerEndpoint.play();
    }

    private void pause(String sessionId) {
        Session user = users.get(sessionId);

        if (user != null) {
            user.getPlayerEndpoint().pause();
        }
    }

    private void resume(final WebSocketSession session) {
        Session user = users.get(session.getId());

        if (user != null) {
            user.getPlayerEndpoint().play();
            VideoInfo videoInfo = user.getPlayerEndpoint().getVideoInfo();

            JsonObject response = new JsonObject();
            response.addProperty("id", "videoInfo");
            response.addProperty("isSeekable", videoInfo.getIsSeekable());
            response.addProperty("initSeekable", videoInfo.getSeekableInit());
            response.addProperty("endSeekable", videoInfo.getSeekableEnd());
            response.addProperty("videoDuration", videoInfo.getDuration());
            sendMessage(session, response.toString());
        }
    }

    private void stop(String sessionId) {
        Session user = users.remove(sessionId);

        if (user != null) {
            user.release();
        }
    }

    private void debugDot(final WebSocketSession session) {
        Session user = users.get(session.getId());

        if (user != null) {
            final String pipelineDot = user.getMediaPipeline().getGstreamerDot();
            try (PrintWriter out = new PrintWriter("player.dot")) {
                out.println(pipelineDot);
            } catch (IOException ex) {
                log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
            }
            final String playerDot = user.getPlayerEndpoint().getElementGstreamerDot();
            try (PrintWriter out = new PrintWriter("player-decoder.dot")) {
                out.println(playerDot);
            } catch (IOException ex) {
                log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
            }
        }

        ServerManager sm = kurento.getServerManager();
        log.warn("[Handler::debugDot] CPU COUNT: {}", sm.getCpuCount());
        log.warn("[Handler::debugDot] CPU USAGE: {}", sm.getUsedCpu(1000));
        log.warn("[Handler::debugDot] RAM USAGE: {}", sm.getUsedMemory());
    }

    private void doSeek(final WebSocketSession session, JsonObject jsonMessage) {
        Session user = users.get(session.getId());

        if (user != null) {
            try {
                user.getPlayerEndpoint().setPosition(jsonMessage.get("position").getAsLong());
            } catch (KurentoException e) {
                log.debug("The seek cannot be performed");
                JsonObject response = new JsonObject();
                response.addProperty("id", "seek");
                response.addProperty("message", "Seek failed");
                sendMessage(session, response.toString());
            }
        }
    }

    private void getPosition(final WebSocketSession session) {
        Session user = users.get(session.getId());

        if (user != null) {
            long position = user.getPlayerEndpoint().getPosition();

            JsonObject response = new JsonObject();
            response.addProperty("id", "position");
            response.addProperty("position", position);
            sendMessage(session, response.toString());
        }
    }

    private void onIceCandidate(String sessionId, JsonObject jsonMessage) {
        Session user = users.get(sessionId);

        if (user != null) {
            JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
            IceCandidate candidate =
                    new IceCandidate(jsonCandidate.get("candidate").getAsString(), jsonCandidate
                            .get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
            user.getWebRtcEndpoint().addIceCandidate(candidate);
        }
    }

    public void sendPlayEnd(WebSocketSession session) {
        if (users.containsKey(session.getId())) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "playEnd");
            sendMessage(session, response.toString());
        }
    }

    private void sendError(WebSocketSession session, String message) {
        if (users.containsKey(session.getId())) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "error");
            response.addProperty("message", message);
            sendMessage(session, response.toString());
        }
    }

    private synchronized void sendMessage(WebSocketSession session, String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Exception sending message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        stop(session.getId());
    }
}
