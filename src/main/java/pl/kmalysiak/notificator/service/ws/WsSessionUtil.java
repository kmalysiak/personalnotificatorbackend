package pl.kmalysiak.notificator.service.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;
import pl.kmalysiak.notificator.model.GuidResult;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.util.Map;

@Slf4j
public class WsSessionUtil {


    public static final String FCM_TOKEN = "fcmTokenAttribute";
    public static final String GUID = "userFirebaseGuidAttribute";
    public static final String EMAIL = "email";

    private WsSessionUtil() {
    }

    public static String getFcmToken(WebSocketSession session) {
        return get(session, FCM_TOKEN);
    }

    public static String getUserFirebaseGuid(WebSocketSession session) {
        return get(session, GUID);
    }

    public static UserTokenId getUserTokenId(WebSocketSession session){
        return new UserTokenId(getUserFirebaseGuid(session), getFcmToken(session));
    }


    private static String get(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        if (val == null) throw new IllegalStateException("WS session missing attribute: " + key);
        return (String) val;
    }

    public static void putWsAttributes(Map<String, Object> attributes, String fcmToken, GuidResult result) {
        attributes.put("fcmTokenAttribute", fcmToken);
        attributes.put("userFirebaseGuidAttribute", result.guid());
        attributes.put("email", result.email());
    }

}
