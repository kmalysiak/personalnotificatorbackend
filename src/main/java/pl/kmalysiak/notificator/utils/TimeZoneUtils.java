package pl.kmalysiak.notificator.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

public class TimeZoneUtils {
    public static final ZoneId TIME_Z_WARSAW = ZoneId.of("Europe/Warsaw");

    public static LocalDateTime getLocalDateTimeNow() {
        return LocalDateTime.now(TIME_Z_WARSAW);
    }

    public static String epochSecondsNow() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    public static LocalDateTime epochSecondsToLocalDateTime(String epochSeconds) {

        return Instant.ofEpochSecond(Long.valueOf(epochSeconds))
                .atZone(TIME_Z_WARSAW)
                .toLocalDateTime();
    }

    public static Long toUtcTimestamp(LocalDateTime timeStamp) {
        return Optional.ofNullable(timeStamp).map(c -> {
            ZoneOffset offset = TimeZoneUtils.TIME_Z_WARSAW.getRules().getOffset(timeStamp);
            return timeStamp.toEpochSecond(offset);
        }).orElse(null);
    }


    public static String toEpochSeconds(LocalDateTime timeStamp) {
        return Optional.ofNullable(timeStamp).map(c -> {
            ZoneOffset offset = TimeZoneUtils.TIME_Z_WARSAW.getRules().getOffset(timeStamp);
            return String.valueOf(timeStamp.toEpochSecond(offset));
        }).orElse(null);
    }
}
