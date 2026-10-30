package com.huddle.backend.config;

import com.huddle.backend.security.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        this(authInterceptor, allowedOrigins, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
                           @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins,
                           org.springframework.core.env.Environment environment) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = validateAndResolveOrigins(allowedOrigins, environment);
    }

    private static List<String> validateAndResolveOrigins(List<String> origins, org.springframework.core.env.Environment environment) {
        boolean isProd = environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        List<String> cleaned = (origins != null)
                ? origins.stream()
                        .filter(s -> s != null && !s.trim().isEmpty())
                        .map(String::trim)
                        .toList()
                : List.of();

        if (isProd) {
            if (cleaned.isEmpty()) {
                throw new IllegalStateException("WebSocket allowed origins (app.cors.allowed-origins) must be explicitly configured in production profile.");
            }
            if (cleaned.contains("*")) {
                throw new IllegalStateException("Wildcard origin '*' is not permitted for WebSocket in production profile.");
            }
            boolean allLocalhost = cleaned.stream().allMatch(o -> o.startsWith("http://localhost") || o.startsWith("http://127.0.0.1"));
            if (allLocalhost) {
                throw new IllegalStateException("WebSocket allowed origins in production cannot fall back to localhost. Provide explicit production origins.");
            }
            return cleaned;
        }

        return cleaned.isEmpty() ? List.of("http://localhost:3000") : cleaned;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = allowedOrigins.toArray(new String[0]);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(patterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
