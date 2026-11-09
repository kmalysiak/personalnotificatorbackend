package pl.kmalysiak.notificator.service.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.kmalysiak.notificator.model.UserToken;
import pl.kmalysiak.notificator.model.UserTokenId;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Wspólny logger monitoringu dostarczania WS/FCM. Wszystkie linie mają prefiks {@code [WSMON]}
 * i krótki identyfikator urządzenia {@code dev=…<6 ostatnich znaków tokenu FCM>}, żeby przebieg
 * nocy dało się odtworzyć jednym {@code grep WSMON} (ew. {@code grep "dev=…abc123"} dla jednego
 * telefonu). Logowane są tylko zdarzenia (zmiany stanu) + okresowe podsumowanie — nic "co tick".
 * <p>
 * Zdarzenia: CONNECT, REPLAY, CLOSE, DEAD, NOTIFY, WAKE, SLEEP-OK, GIVE-UP, STATUS.
 * Poziom loggera {@code pl.kmalysiak.notificator.wsmon} można zmienić osobno w logback.
 */
public final class WsMon {
    public static final Logger LOG = LoggerFactory.getLogger("pl.kmalysiak.notificator.wsmon");

    private WsMon() {
    }

    public static String dev(UserTokenId id) {
        return id == null ? "dev=?" : "dev=…" + tail(id.getFcmToken());
    }

    public static String dev(UserToken ut) {
        return ut == null ? "dev=?" : "dev=…" + tail(ut.getFcmToken()) + " (" + ut.getEmail() + ")";
    }

    public static String ago(LocalDateTime from, LocalDateTime now) {
        return from == null ? "-" : Duration.between(from, now).getSeconds() + "s";
    }

    private static String tail(String token) {
        if (token == null) return "?";
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }
}
