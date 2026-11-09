package pl.kmalysiak.notificator.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaEntityDtoTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 9, 14, 51, 53, 871292000);

    private static HaEntityDto dto(String freq, LocalDateTime prev, LocalDateTime curr) {
        HaEntityDto d = new HaEntityDto();
        d.setNotifyFreq(freq);
        d.setPrevTimestamp(prev);
        d.setCurrTimestamp(curr);
        return d;
    }

    @Test
    void pt0sWysylaPrzyTymSamymZnacznikuCzasu() {
        assertTrue(dto("PT0S", T, T).shouldSendMobileNotification());
    }

    @Test
    void pt0sWysylaPoKazdejZmianie() {
        assertTrue(dto("PT0S", T, T.plusNanos(1000)).shouldSendMobileNotification());
        assertTrue(dto("PT0S", null, T).shouldSendMobileNotification());
    }

    @Test
    void pt1sNadalDlawi() {
        assertFalse(dto("PT1S", T, T).shouldSendMobileNotification());
        assertFalse(dto("PT1S", T, T.plusSeconds(1)).shouldSendMobileNotification());
        assertTrue(dto("PT1S", T, T.plusSeconds(2)).shouldSendMobileNotification());
    }

    @Test
    void p100yNigdy() {
        assertFalse(dto("P100Y", T, T.plusDays(1)).shouldSendMobileNotification());
    }
}
