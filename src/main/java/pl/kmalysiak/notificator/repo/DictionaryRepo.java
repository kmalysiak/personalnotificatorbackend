package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.kmalysiak.notificator.model.entity.DictionaryEntity;

import java.util.Optional;

public interface DictionaryRepo extends JpaRepository<DictionaryEntity, Long> {
    Optional<DictionaryEntity> findByDictNumAndCode(Integer dictNum, String code);
}
