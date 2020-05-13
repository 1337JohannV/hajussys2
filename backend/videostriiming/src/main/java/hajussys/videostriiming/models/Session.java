package hajussys.videostriiming.models;

import com.google.gson.JsonObject;
import lombok.Data;
import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Data
public class Session {
    private static final Logger log = LoggerFactory.getLogger(Session.class);

    private final WebSocketSession session;
    private WebRtcEndpoint webRtcEndpoint;
    private String id;
    private RecorderEndpoint recorderEndpoint;
    private MediaPipeline mediaPipeline;
    private Date stopTimestamp;
    private PlayerEndpoint playerEndpoint;
    private Date startTimeStamp;

    public Session(WebSocketSession session) {
        this.session = session;
        this.id = session.getId();
        this.startTimeStamp = new Date();
    }

    public void sendMessage(JsonObject message) throws IOException {
        log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
        session.sendMessage(new TextMessage(message.toString()));
    }

    public void addCandidate(IceCandidate candidate) {
        webRtcEndpoint.addIceCandidate(candidate);
    }

    public void stop() {
        if (recorderEndpoint != null) {
            final CountDownLatch stoppedCountDown = new CountDownLatch(1);
            ListenerSubscription subscriptionId = recorderEndpoint
                    .addStoppedListener(event -> stoppedCountDown.countDown());
            recorderEndpoint.stop();
            try {
                if (!stoppedCountDown.await(5, TimeUnit.SECONDS)) {
                    log.error("Error waiting for recorder to stop");
                }
            } catch (InterruptedException e) {
                log.error("Exception while waiting for state change", e);
            }
            recorderEndpoint.removeStoppedListener(subscriptionId);
        }
    }

    public void release() {
        this.mediaPipeline.release();
        this.webRtcEndpoint = null;
        this.mediaPipeline = null;
        if (this.stopTimestamp == null) {
            this.stopTimestamp = new Date();
        }
    }
}
