package pl.kmalysiak.notificator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import pl.kmalysiak.notificator.model.Notification;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.NotificationMeta;
import pl.kmalysiak.notificator.model.SendRequest;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private final String projectId;
    private final GoogleCredentials credentials;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationService(@Value("${firebase.project-id}") String projectId, @Value("${firebase.credentials}") String credentialsPath) throws IOException {

        this.projectId = projectId;
        FileInputStream resource = new FileInputStream(credentialsPath);
        this.credentials = GoogleCredentials.fromStream(resource).createScoped("https://www.googleapis.com/auth/firebase.messaging");
    }

    @SneakyThrows
    public void sendMessage(String fcmToken, String data, String msg) {
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        URL url = new URL("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json; UTF-8");
        conn.setDoOutput(true);
        Notification not = new Notification(fcmToken, new NotificationData(data, msg), new NotificationMeta("high"));
        Map<String, Object> message = Map.of("message", not);
        String body = mapper.writeValueAsString(message);
        log.info("Sending:{}", body);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int responseCode = conn.getResponseCode();

        if (responseCode != 200) {
            throw new IOException("FCM Error: " + responseCode + " - " + conn.getResponseMessage());
        } else {
            log.info("Sent:" + responseCode);
        }
    }


}
