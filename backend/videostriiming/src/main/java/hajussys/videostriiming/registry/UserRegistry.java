package hajussys.videostriiming.registry;

import hajussys.videostriiming.models.Session;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

public class UserRegistry {

    private ConcurrentHashMap<String, Session> usersBySessionId = new ConcurrentHashMap<>();

    public void register(Session user) {
        usersBySessionId.put(user.getId(), user);
    }

    public Session getById(String id) {
        return usersBySessionId.get(id);
    }

    public Session getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    public boolean exists(String id) {
        return usersBySessionId.keySet().contains(id);
    }

    public Session removeBySession(WebSocketSession session) {
        final Session user = getBySession(session);
        if (user != null) {
            usersBySessionId.remove(session.getId());
        }
        return user;
    }

}