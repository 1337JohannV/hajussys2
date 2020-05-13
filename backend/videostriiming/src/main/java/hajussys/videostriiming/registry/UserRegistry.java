package hajussys.videostriiming.registry;

import hajussys.videostriiming.models.FileModel;
import hajussys.videostriiming.models.Session;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class UserRegistry {

    public ConcurrentHashMap<String, Session> usersBySessionId = new ConcurrentHashMap<>();

    public List<FileModel> pastStreams = new ArrayList<>();

    public void addStream(FileModel fileModel) {
        pastStreams.add(fileModel);
    }

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

    public Session removeById(String id) {
        return usersBySessionId.remove(id);
    }

}