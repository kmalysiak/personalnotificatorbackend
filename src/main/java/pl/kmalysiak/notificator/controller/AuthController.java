package pl.kmalysiak.notificator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pl.kmalysiak.notificator.model.FcmToken;
import pl.kmalysiak.notificator.model.GuidResult;
import pl.kmalysiak.notificator.model.RegisterResult;
import pl.kmalysiak.notificator.model.UserTokenId;
import pl.kmalysiak.notificator.rabbit.RabbitPublisher;
import pl.kmalysiak.notificator.rabbit.model.SingleUidNotification;
import pl.kmalysiak.notificator.service.TokenVerifierService;
import pl.kmalysiak.notificator.service.UserTokenService;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.time.ZoneOffset;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final TokenVerifierService verifier;
    private final UserTokenService userTokenService;
    private final RabbitPublisher rabbitPublisher;

    @PostMapping("/register")
    public RegisterResult registerToken(@RequestHeader("Authorization") String authHeader, @RequestBody FcmToken request) throws Exception {
        String idToken = authHeader.replace("Bearer ", "");
        log.info("Received idToken:{}", idToken);
        GuidResult uidRes = verifier.verifyAndGetUid(idToken);
        //idToken — token JWT wystawiany przez Firebase Authentication po zalogowaniu użytkownika na Androidzie
        //Podpisany przez Google, weryfikowalny bez kontaktu z Firebase — verifier.verifyAndGetUid() sprawdza podpis i wyciąga uid. Krótkotrwały (domyślnie 1h).


        //guid — Firebase UID (uid), unikalny identyfikator użytkownika w Firebase Auth. Stały przez cały czas życia konta.

        //fcmToken — token ##apki## na ##urządzeniu#. Służy do wysyłania push. Wielu userów w jednej apcje na 1 urzadzeniu - nie da sie golym tokenem, trzeba kombinowac z payloadem
        //Zmienny — rotuje przy reinstalacji, wyczyszczeniu danych, czasem samoistnie


        if (uidRes.isOk()) {
            userTokenService.onUserFcmTokenAuthorised(new UserTokenId(uidRes.guid(), request.fcmToken()), uidRes.email());
            rabbitPublisher.pushUserUpdateNotification(new SingleUidNotification(uidRes.guid(), uidRes.email()));
            return new RegisterResult(true, "");
        } else {
            log.info("User not registered:{}", uidRes.email());
            return new RegisterResult(false, uidRes.errorDesc());
        }
    }

    @GetMapping("/heartbeat")
    public String heartbeat() {
        return "Behold! So... The essence of the world is: " + TimeZoneUtils.getLocalDateTimeNow().toEpochSecond(ZoneOffset.UTC);
    }

}