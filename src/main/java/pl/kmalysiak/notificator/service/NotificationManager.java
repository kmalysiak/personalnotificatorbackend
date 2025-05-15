package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationManager {

    private final NotificationService notificationService;
    private final UserService userService;

    public void sendMsgToAllUsers(String type, String msgContent) {
        List<String> tokens = userService.getAllFcmTokens();
        if (CollectionUtils.isEmpty(tokens)) throw new RuntimeException("No tokens");
        for (String token : tokens) {
            notificationService.sendMessage(token, type, msgContent);
        } //TODO: list tokens directly to GOOGLE API
    }

    public void sendMsgToUser(String userUid, String type, String msgBody) {
        List<String> tokens = userService.getFcmTokensForGuid(userUid);
        if (CollectionUtils.isEmpty(tokens)) log.error("No tokens for UID: " + userUid);

        tokens.forEach(token -> notificationService.sendMessage(token, type, msgBody));
    }

    public void sendMsgToUsers(Set<String> logins, String type, String msgBody) {
       for(String login :logins) {

           List<String> tokens = userService.getFcmTokensForLogin(login);
           if (CollectionUtils.isEmpty(tokens))
               log.error("No tokens for login: " + login);
           tokens.forEach(token -> notificationService.sendMessage(token, type, msgBody));
       }
    }
}
