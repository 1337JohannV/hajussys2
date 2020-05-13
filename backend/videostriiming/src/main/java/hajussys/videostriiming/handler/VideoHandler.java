package hajussys.videostriiming.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hajussys.videostriiming.models.Session;
import hajussys.videostriiming.models.User;
import hajussys.videostriiming.models.UserSession;
import hajussys.videostriiming.registry.UserRegistry;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class VideoHandler extends TextWebSocketHandler {
    private String RECORDER_FILE_PATH;
    private static final Gson gson = new GsonBuilder().create();
    private final Logger log = LoggerFactory.getLogger(VideoHandler.class);
    private final ConcurrentHashMap<String, UserSession> viewers = new ConcurrentHashMap<>();


    @Autowired
    private UserRegistry registry;

    @Autowired
    private KurentoClient kurento;

    private MediaPipeline pipeline;
    private Session presenterUserSession;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

        // log.info("Incoming message: {}", jsonMessage);

        Session user = registry.getBySession(session);
        if (user != null) {
            log.info("Incoming message from user '{}': {}", user.getId(), jsonMessage);
        } else {
            log.info("Incoming message from new user: {}", jsonMessage);
        }

        switch (jsonMessage.get("id").getAsString()) {
            case "start":
                start(session, jsonMessage);
                break;
            case "stop":
                if (user != null) {
                    user.stop();
                }
            case "stopPlay":
                if (user != null) {
                    user.release();
                }
                break;
            case "play":
                play(user, session, jsonMessage);
                break;
            case "viewer":
                log.info("CASE ON VIEWER!!!!!!!!!!!!!!!! {}", jsonMessage);
                try {
                    viewer(session, jsonMessage);
                } catch (Throwable t) {
                    log.info("Viewer request case: {}", t.toString());
                }
                break;
            case "onIceCandidate":
                log.info("JSON MESSAGE!!!!!!!!!!!: {}", jsonMessage);
                JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
                log.info("Peale seda sitta");
                System.out.println("CANDIDATE" + jsonCandidate.toString());


                if (presenterUserSession != null) {
                    if (presenterUserSession.getSession() == session) {
                        user = presenterUserSession;
                    } else {
                        user = registry.getById(session.getId());
                    }
                }

                if (user != null) {
                    IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
                            jsonCandidate.get("sdpMid").getAsString(),
                            jsonCandidate.get("sdpMLineIndex").getAsInt());
                    user.addCandidate(candidate);
                }
                break;
            default:
                sendError(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        registry.removeBySession(session);
    }

    private synchronized void start(final WebSocketSession session, JsonObject jsonMessage) {
        try {

            // 1. Media logic (webRtcEndpoint in loopback)
            if (presenterUserSession == null) {
                presenterUserSession = new Session(session);

                pipeline = kurento.createMediaPipeline();
                log.info("MINGI TÜRA ÜRASK {}", presenterUserSession);
                log.info("MINGI TÜRA ÜRASK pipeline: {}", pipeline);

                presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(pipeline).build());
                WebRtcEndpoint webRtcEndpoint = presenterUserSession.getWebRtcEndpoint();

                // WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
                webRtcEndpoint.connect(webRtcEndpoint);

                MediaProfileSpecType profile = getMediaProfileFromMessage(jsonMessage);

                RECORDER_FILE_PATH = String.format("file:///tmp/%s.webm", session.getId());

                RecorderEndpoint recorder = new RecorderEndpoint.Builder(pipeline, RECORDER_FILE_PATH)
                        .withMediaProfile(profile).build();

                recorder.addRecordingListener(event -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "recording");
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                });

                recorder.addStoppedListener(event -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "stopped");
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                });

                recorder.addPausedListener(event -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "paused");
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                });

                connectAccordingToProfile(webRtcEndpoint, recorder, profile);

                // 2. Store user session
                Session user = new Session(session);
                user.setMediaPipeline(pipeline);
                user.setWebRtcEndpoint(webRtcEndpoint);
                user.setRecorderEndpoint(recorder);
                registry.register(user);

                // 3. SDP negotiation
                String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
                String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

                // 4. Gather ICE candidates
                webRtcEndpoint.addIceCandidateFoundListener(event -> {
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "iceCandidate");
                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                    log.debug("added ice candidate");
                    try {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                });

                JsonObject response = new JsonObject();
                response.addProperty("id", "startResponse"); // presenterResponse
                response.addProperty("sdpAnswer", sdpAnswer);
                response.addProperty("response", "accepted");


                synchronized (session) {
                    session.sendMessage(new TextMessage(response.toString()));
                }

                webRtcEndpoint.gatherCandidates();

                recorder.record();
            } else {
                JsonObject response = new JsonObject();
                response.addProperty("id", "presenterResponse");
                response.addProperty("response", "rejected");
                response.addProperty("message",
                        "Another user is currently acting as sender. Try again later ...");
                session.sendMessage(new TextMessage(response.toString()));
            }
        } catch (Throwable t) {
            log.error("Start error", t);
            sendError(session, t.getMessage());
        }
    }

    private MediaProfileSpecType getMediaProfileFromMessage(JsonObject jsonMessage) {

        MediaProfileSpecType profile;
        System.out.println(jsonMessage);
        switch (jsonMessage.get("mode").getAsString()) {
            case "audio-only":
                profile = MediaProfileSpecType.WEBM_AUDIO_ONLY;
                break;
            case "video-only":
                profile = MediaProfileSpecType.WEBM_VIDEO_ONLY;
                break;
            default:
                profile = MediaProfileSpecType.WEBM;
        }

        return profile;
    }

    private void connectAccordingToProfile(WebRtcEndpoint webRtcEndpoint, RecorderEndpoint recorder,
                                           MediaProfileSpecType profile) {
        switch (profile) {
            case WEBM:
                webRtcEndpoint.connect(recorder, MediaType.AUDIO);
                webRtcEndpoint.connect(recorder, MediaType.VIDEO);
                break;
            case WEBM_AUDIO_ONLY:
                webRtcEndpoint.connect(recorder, MediaType.AUDIO);
                break;
            case WEBM_VIDEO_ONLY:
                webRtcEndpoint.connect(recorder, MediaType.VIDEO);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported profile for this tutorial: " + profile);
        }
    }

    private void play(Session user, final WebSocketSession session, JsonObject jsonMessage) {
        try {

            // 1. Media logic\

            System.out.println(user + "USER");

            final MediaPipeline pipeline = kurento.createMediaPipeline();
            System.out.println("PIPELINE: " + pipeline);
            WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
            String path = jsonMessage.get("filepath") != null ? jsonMessage.get("filepath").getAsString() : null;
            String pathString = String.format("file:///tmp/%s.webm", path);
            System.out.println("PATHSTRING: "+pathString);
            PlayerEndpoint player = new PlayerEndpoint
                    .Builder(pipeline, path == null
                    ? RECORDER_FILE_PATH : pathString ).build();
            player.connect(webRtcEndpoint);

            // Player listeners
            player.addErrorListener(event -> {
                log.info("ErrorEvent for session '{}': {}", session.getId(), event.getDescription());
                sendPlayEnd(session, pipeline);
            });
            player.addEndOfStreamListener(event -> {
                log.info("EndOfStreamEvent for session '{}'", session.getId());
                sendPlayEnd(session, pipeline);
            });

            // 2. Store user session
            if (user == null) {
                user = new Session(session);
            }
            user.setMediaPipeline(pipeline);
            user.setWebRtcEndpoint(webRtcEndpoint);

            // 3. SDP negotiation
            String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
            String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

            JsonObject response = new JsonObject();
            response.addProperty("id", "playResponse");
            response.addProperty("sdpAnswer", sdpAnswer);

            // 4. Gather ICE candidates
            webRtcEndpoint.addIceCandidateFoundListener(event -> {
                JsonObject response1 = new JsonObject();
                response1.addProperty("id", "iceCandidate");
                response1.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(response1.toString()));
                    }
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            });

            // 5. Play recorded stream
            player.play();

            synchronized (session) {
                session.sendMessage(new TextMessage(response.toString()));
            }

            webRtcEndpoint.gatherCandidates();
        } catch (Throwable t) {
            log.error("Play error", t);
            sendError(session, t.getMessage());
        }
    }

    public void sendPlayEnd(WebSocketSession session, MediaPipeline pipeline) {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("id", "playEnd");
            session.sendMessage(new TextMessage(response.toString()));
        } catch (IOException e) {
            log.error("Error sending playEndOfStream message", e);
        }
        // Release pipeline
        pipeline.release();
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("id", "error");
            response.addProperty("message", message);
            session.sendMessage(new TextMessage(response.toString()));
        } catch (IOException e) {
            log.error("Exception sending message", e);
        }
    }


    private synchronized void viewer(final WebSocketSession session, JsonObject jsonMessage)
            throws IOException {
        if (presenterUserSession == null || presenterUserSession.getWebRtcEndpoint() == null) {
            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "rejected");
            response.addProperty("message",
                    "No active sender now. Become sender or . Try again later ...");
            session.sendMessage(new TextMessage(response.toString()));
        } else {
            if (registry.getById(session.getId()) != null) {
                JsonObject response = new JsonObject();
                response.addProperty("id", "viewerResponse");
                response.addProperty("response", "rejected");
                response.addProperty("message", "You are already viewing in this session. "
                        + "Use a different browser to add additional viewers.");
                session.sendMessage(new TextMessage(response.toString()));
                return;
            }
            Session viewer = new Session(session);
            registry.register(viewer);

            WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(pipeline).build();

            nextWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

                @Override
                public void onEvent(IceCandidateFoundEvent event) {
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
                }
            });

            viewer.setWebRtcEndpoint(nextWebRtc);
            presenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
            String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
            String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

            JsonObject response = new JsonObject();
            response.addProperty("id", "viewerResponse");
            response.addProperty("response", "accepted");
            response.addProperty("sdpAnswer", sdpAnswer);

            synchronized (session) {
                viewer.sendMessage(response);
            }
            nextWebRtc.gatherCandidates();
        }
    }

}