package pl.kmalysiak.notificator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pl.kmalysiak.notificator.model.GuidResult;
import pl.kmalysiak.notificator.model.RegisterResult;
import pl.kmalysiak.notificator.model.TokenRequest;
import pl.kmalysiak.notificator.rabbit.RabbitPublisher;
import pl.kmalysiak.notificator.rabbit.model.SingleUidNotification;
import pl.kmalysiak.notificator.service.TokenVerifierService;
import pl.kmalysiak.notificator.service.UserService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final TokenVerifierService verifier;
    private final UserService userService;
    private final RabbitPublisher rabbitPublisher;

    @PostMapping("/register")
    public RegisterResult registerToken(@RequestHeader("Authorization") String authHeader, @RequestBody TokenRequest request) throws Exception {
        String idToken = authHeader.replace("Bearer ", "");
        log.info("Received idToken:{}", idToken);
        GuidResult uidRes = verifier.verifyAndGetUid(idToken);

        if (uidRes.isOk()) {
            userService.saveUserToken(uidRes.guid(), request.fcmToken(), request.login() , LocalDateTime.now());
            rabbitPublisher.pushUserUpdateNotification(new SingleUidNotification(uidRes.guid(), "User logged in:" + request.login()));
            return new RegisterResult(true, "");
        } else {
            log.info("User not registered:{}", request.login());
            return new RegisterResult(false, uidRes.errorDesc());
        }
    }

    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "Behold! So... The essence of the world is: " + LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

}