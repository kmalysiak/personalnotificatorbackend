package pl.kmalysiak.notificator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pojedynczy błąd/uwaga przypisana do konkretnego pola DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldError {
    private String field;
    private String comment;
}
