package pl.kmalysiak.notificator.rabbit.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleUidNotification implements Serializable {
    private String uid;
    private String msg;
}
