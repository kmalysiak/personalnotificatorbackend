package pl.kmalysiak.notificator.model;


import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
public class UserTokenId  implements Serializable {
    private String userFirebaseGuid;
    private String fcmToken;


    @Transient
    public String getDesc(){
        return  String.format("fiGuid:%s fcmToken:%s", userFirebaseGuid, fcmToken);
    }

    public boolean isValid(){
        return userFirebaseGuid != null  && fcmToken != null;
    }

}