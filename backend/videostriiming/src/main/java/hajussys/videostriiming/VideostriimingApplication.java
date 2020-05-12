package hajussys.videostriiming;

import hajussys.videostriiming.handler.VideoHandler;
import hajussys.videostriiming.registry.UserRegistry;
import org.kurento.client.KurentoClient;
import org.kurento.client.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@EnableWebSocket
@SpringBootApplication
public class VideostriimingApplication implements WebSocketConfigurer {
    static final String KMS_URL = "ws://192.168.0.24:8888/kurento";
    @Bean
    public VideoHandler handler() {
        return new VideoHandler();
    }

    @Bean
    public KurentoClient kurentoClient() {
        return KurentoClient.create(KMS_URL);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("*").allowedOrigins("*");
            }
        };
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler(), "/recording").setAllowedOrigins("*");
    }

    @Bean
    public UserRegistry registry() {
        return new UserRegistry();
    }


    public static void main(String[] args) {
        SpringApplication.run(VideostriimingApplication.class, args);
    }

}
