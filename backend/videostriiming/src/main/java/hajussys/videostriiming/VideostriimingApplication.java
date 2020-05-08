package hajussys.videostriiming;

import hajussys.videostriiming.handler.VideoHandler;
import hajussys.videostriiming.registry.UserRegistry;
import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@EnableWebSocket
@SpringBootApplication
public class VideostriimingApplication implements WebSocketConfigurer {

    @Bean
    public VideoHandler handler() {
        return new VideoHandler();
    }

    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler(), "/recording");
    }

    @Bean
    public UserRegistry registry() {
        return new UserRegistry();
    }


    public static void main(String[] args) {
        SpringApplication.run(VideostriimingApplication.class, args);
    }

}
