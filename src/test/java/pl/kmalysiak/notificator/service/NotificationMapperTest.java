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

import static org.mockito.Mockito.when;

class NotificationMapperTest {


    private NotificationTemplateRepository templateRepository;
    private NotificationMapper notificationMapper;

    @BeforeEach
    void setup() {
        templateRepository = Mockito.mock(NotificationTemplateRepository.class);
        this.notificationMapper = new NotificationMapper(templateRepository);
        notificationMapper.configureFreemarker();
    }

    @Test
    void renderEventTemplate() throws Exception {
        // Define template in test
        NotificationTemplateEntity ent = new NotificationTemplateEntity();
        ent.setId(1L);
        ent.setTypeTemplate(loadFile("binary_sensor.move_stairs_type.ftl"));
        ent.setStatusTemplate(loadFile("binary_sensor.move_stairs_status.ftl"));



        // Mock repository to return our test template
        when(templateRepository.findByEntityId("ent_xyz")).thenReturn(Optional.of(ent));

        HaEntityDto haEnt = new HaEntityDto();
        haEnt.setEntityId("ent_xyz");
        haEnt.setCurrState("on");
        haEnt.setCurrTimestamp(LocalDateTime.of(2022, 1,1,1,2,3,400));
        Pair<String, String> res = notificationMapper.getNotificationTypeAndStatus(haEnt);


        String expectedJson = "{ \"type\": \"sensor\", \"status\": \"on\" }";
        //assertEquals(expectedJson, out.toString().trim());
    }

//        @Test
//        void renderEventTemplateWithMap() throws Exception {
//            // Alternative: Map as scope
//            String template = "{ \"type\": \"{{type}}\", \"status\": \"{{status}}\" }";
//            when(templateRepository.getTemplateByType("sensor")).thenReturn(template);
//
//            Map<String,Object> eventMap = new HashMap<>();
//            eventMap.put("type", "sensor");
//            eventMap.put("status", "off");
//
//            String templateFromRepo = templateRepository.getTemplateByType("sensor");
//            Mustache mustache = mustacheFactory.compile(new StringReader(templateFromRepo), "tpl");
//
//            StringWriter out = new StringWriter();
//            mustache.execute(out, eventMap);
//            out.flush();
//
//            String expectedJson = "{ \"type\": \"sensor\", \"status\": \"off\" }";
//            assertEquals(expectedJson, out.toString().trim());
//        }


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

