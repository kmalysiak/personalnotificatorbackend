package pl.kmalysiak.notificator;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pl.kmalysiak.notificator.model.GuidResult;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.service.TokenVerifierService;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.service.ws.NotificationHandler;
import pl.kmalysiak.notificator.service.ws.WsSessionUtil;

import java.util.Map;


@Configuration
@RequiredArgsConstructor
@EnableWebSocket
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationHandler notificationHandler;
    private final TokenVerifierService verifier;
    private final UserTokenService userTokenService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notifications")
                .addInterceptors()
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
                        // Android wysyła naglowki:
                        // Authorization: Bearer <idToken> - kompletny podpisany JWT
                        // X-FCM-Token -po prostu fcm token

                        String authHeader = ((ServletServerHttpRequest) request).getServletRequest().getHeader("Authorization");

                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        }

                        String fcmToken = null;
                        if (request instanceof ServletServerHttpRequest servletRequest) {
                            fcmToken = servletRequest.getServletRequest().getHeader("X-FCM-Token");
                        }

                        if (fcmToken == null || fcmToken.isBlank()) {
                            response.setStatusCode(HttpStatus.BAD_REQUEST);
                            return false;
                        }

                        String idToken = authHeader.replace("Bearer ", "");
                        GuidResult result = verifier.verifyAndGetUid(idToken);

                        if (!result.isOk()) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            log.info("Odmówiono autoryzacji uid={} fcmToken={} email={}", result.guid(), fcmToken, result.email());
                            return false;
                        }


                        WsSessionUtil.putWsAttributes(attributes, fcmToken, result);
                        userTokenService.onUserFcmTokenAuthorised(new UserTokenId(result.guid(), fcmToken), result.email());
                        log.info("Zautoryzowano uid={} fcmToken={} email:{}", result.guid(), fcmToken, result.email());

                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

                    }
                })
                .setAllowedOrigins("*");
    }


}