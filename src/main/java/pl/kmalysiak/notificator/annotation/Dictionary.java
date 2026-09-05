package pl.kmalysiak.notificator.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Oznacza pole DTO typu {@link pl.kmalysiak.notificator.dto.DictValue} jako wymagające
 * tłumaczenia kodu na czytelną wartość ze słownika o numerze {@link #value()}.
 * Odczytywane refleksyjnie przez {@link pl.kmalysiak.notificator.service.DictionaryService}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dictionary {
    int value();
}
