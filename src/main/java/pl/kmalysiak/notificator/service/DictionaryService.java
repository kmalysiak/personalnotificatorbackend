package pl.kmalysiak.notificator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.annotation.Dictionary;
import pl.kmalysiak.notificator.dto.DictValue;
import pl.kmalysiak.notificator.model.entity.DictionaryEntity;
import pl.kmalysiak.notificator.repo.DictionaryRepo;

import java.lang.reflect.Field;

/**
 * Generyczny mechanizm tłumaczenia kodów statusów w DTO. Pole DTO oznaczone
 * {@code @Dictionary(nr)} musi być typu {@link DictValue} z ustawionym już {@code kod}
 * (value może być {@code null}) - {@link #resolveDictionaryFields(Object)} dogrywa
 * odpowiadającą {@code value} ze słownika o numerze {@code nr}, dopasowując po {@code kod}.
 * <p>
 * Użycie w serwisie budującym DTO: ustaw wszystkie pola (w tym {@code @Dictionary}-owe, jako
 * {@code new DictValue(kod, null)}), a na końcu wywołaj {@code resolveDictionaryFields(dto)}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DictionaryService {

    private final DictionaryRepo repo;

    public void resolveDictionaryFields(Object dto) {
        for (Field field : dto.getClass().getDeclaredFields()) {
            Dictionary annotation = field.getAnnotation(Dictionary.class);
            if (annotation != null) {
                resolveField(dto, field, annotation.value());
            }
        }
    }

    private void resolveField(Object dto, Field field, int dictNum) {
        field.setAccessible(true);
        try {
            Object raw = field.get(dto);
            if (!(raw instanceof DictValue current) || current.getKod() == null) {
                return;
            }

            String value = repo.findByDictNumAndCode(dictNum, current.getKod())
                    .map(DictionaryEntity::getValue)
                    .orElseGet(() -> {
                        log.warn("Brak wpisu w słowniku nr:{} dla kodu:{} (pole:{})", dictNum, current.getKod(), field.getName());
                        return current.getKod();
                    });

            field.set(dto, new DictValue(current.getKod(), value));
        } catch (IllegalAccessException e) {
            log.error("Nie udało się odczytać/ustawić pola słownikowego:{}", field.getName(), e);
        }
    }
}
