package pl.kmalysiak.notificator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.util.Pair;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.NotificationTemplateEntity;
import pl.kmalysiak.notificator.repo.NotificationTemplateRepository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class NotificationMapperTest {


    private NotificationTemplateRepository templateRepository;
    private NotificationMapper notificationMapper;
    private NotificationTemplateEntity ent = new NotificationTemplateEntity();

    @BeforeEach
    public void suiteSetup() {
        templateRepository = Mockito.mock(NotificationTemplateRepository.class);
        this.notificationMapper = new NotificationMapper(templateRepository);
        notificationMapper.configureFreemarker();

        ent.setId(1L);
        ent.setStatusTemplate(loadFile("binary_sensor.move_stairs_status.ftl"));


    }

    @Test
    public void shouldRenderTestWhenEntityStateAndIdIsKnown() {
        // Mock repository to return our test template
        when(templateRepository.findById(1L)).thenReturn(Optional.of(ent));
        HaEntityDto haEnt = new HaEntityDto();
        haEnt.setEntityId("weather.forecast_dom");
        haEnt.setCurrState("on");
        haEnt.setNotificationTemplateId(1L);
        haEnt.setCurrTimestamp(LocalDateTime.of(2022, 1, 1, 1, 2, 3, 400));
        Pair<String, String> res = notificationMapper.getNotificationTypeAndStatus(haEnt);

        assertEquals("pogoda", res.getFirst());
        assertEquals("otwarty", res.getSecond());
    }

    @Test
    public void shouldRenderTestWhenEntityStateAndIdUnknown() {
        // Mock repository to return our test template
        when(templateRepository.findById(1L)).thenReturn(Optional.of(ent));
        HaEntityDto haEnt = new HaEntityDto();
        haEnt.setEntityId("unknown_id");
        haEnt.setCurrState("some_strange_state");
        haEnt.setNotificationTemplateId(1L);
        haEnt.setCurrTimestamp(LocalDateTime.of(2022, 1, 1, 1, 2, 3, 400));
        Pair<String, String> res = notificationMapper.getNotificationTypeAndStatus(haEnt);

        assertEquals("unknown_id", res.getFirst());
        assertEquals("some_strange_state", res.getSecond());
    }


    public String loadFile(String fname) {
        String path = "src/test/resources/templates/" + fname;
        File file = new File(path);
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

