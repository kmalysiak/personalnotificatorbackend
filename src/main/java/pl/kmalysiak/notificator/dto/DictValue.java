package pl.kmalysiak.notificator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kod + przetłumaczona wartość ze słownika (patrz {@link pl.kmalysiak.notificator.annotation.Dictionary}).
 * {@code value} jest {@code null} dopóki {@link pl.kmalysiak.notificator.service.DictionaryService}
 * go nie uzupełni.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictValue {
    private String kod;
    private String value;
}
