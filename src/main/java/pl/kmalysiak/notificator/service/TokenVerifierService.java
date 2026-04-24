package pl.kmalysiak.notificator.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.model.GuidResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class TokenVerifierService {
    private final GoogleCredentials credentials;

    public TokenVerifierService(@Value("${firebase.credentials}") String credentialsPath) throws IOException {
        FileInputStream resource = new FileInputStream(credentialsPath);
        this.credentials = GoogleCredentials
                .fromStream(resource);

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp.initializeApp(options);
    }

    public GuidResult verifyAndGetUid(String idTokenString) {
        try {
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idTokenString);
            Map<String, Object> firebase = (Map<String, Object>) token.getClaims().get("firebase");
            String signInProvider = (String) firebase.get("sign_in_provider");
            if ("password".equals(signInProvider)) {
                return new GuidResult(true, token.getUid(), token.getEmail(), "");
            } else {
                return new GuidResult(false, "", "", "not authorized");
            }
        } catch (FirebaseAuthException e) {
            return new GuidResult(false, "", "", "not authorized");
        }
    }
}
