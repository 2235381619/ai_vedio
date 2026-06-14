package cn.bugstack.ai.config;

import cn.bugstack.ai.cases.websocket.SpeechWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SpeechWebSocketHandler speechWebSocketHandler;

    public WebSocketConfig(SpeechWebSocketHandler speechWebSocketHandler) {
        this.speechWebSocketHandler = speechWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(speechWebSocketHandler, "/ws")
                .addInterceptors(sessionIdHandshakeInterceptor())
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(1048576);
        container.setMaxBinaryMessageBufferSize(1048576);
        container.setMaxSessionIdleTimeout(600000L);
        return container;
    }

    @Bean
    public SessionIdHandshakeInterceptor sessionIdHandshakeInterceptor() {
        return new SessionIdHandshakeInterceptor();
    }
}
