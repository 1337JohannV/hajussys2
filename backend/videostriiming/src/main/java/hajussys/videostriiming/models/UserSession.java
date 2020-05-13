package hajussys.videostriiming.models;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * User session.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class UserSession {

    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final WebSocketSession session;
    private WebRtcEndpoint webRtcEndpoint;
    private String id;
    private RecorderEndpoint recorderEndpoint;
    private MediaPipeline mediaPipeline;
    private Date stopTimestamp;

    public UserSession(WebSocketSession session) {
        this.session = session;
        this.id = session.getId();
    }

    public WebSocketSession getSession() {
        return session;
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
        session.sendMessage(new TextMessage(message.toString()));
    }

    public WebRtcEndpoint getWebRtcEndpoint() {
        return webRtcEndpoint;
    }

    public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
        this.webRtcEndpoint = webRtcEndpoint;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public void setRecorderEndpoint(RecorderEndpoint recorderEndpoint) {
        this.recorderEndpoint = recorderEndpoint;
    }

    public MediaPipeline getMediaPipeline() {
        return mediaPipeline;
    }

    public void setMediaPipeline(MediaPipeline mediaPipeline) {
        this.mediaPipeline = mediaPipeline;
    }

    public void addCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }

    public Date getStopTimestamp() {
        return stopTimestamp;
    }

//    public void stop() {
//        if (recorderEndpoint != null) {
//            final CountDownLatch stoppedCountDown = new CountDownLatch(1);
//            ListenerSubscription subscriptionId = recorderEndpoint
//                    .addStoppedListener(event -> stoppedCountDown.countDown());
//            recorderEndpoint.stop();
//            try {
//                if (!stoppedCountDown.await(5, TimeUnit.SECONDS)) {
//                    log.error("Error waiting for recorder to stop");
//                }
//            } catch (InterruptedException e) {
//                log.error("Exception while waiting for state change", e);
//            }
//            recorderEndpoint.removeStoppedListener(subscriptionId);
//        }
//    }

    public void release() {
        this.mediaPipeline.release();
        this.webRtcEndpoint = null;
        this.mediaPipeline = null;
        if (this.stopTimestamp == null) {
            this.stopTimestamp = new Date();
        }
    }
}