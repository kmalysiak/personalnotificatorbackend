package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.kmalysiak.notificator.model.entity.HaEntity;

import java.util.Optional;

public interface HaEntityRepo extends JpaRepository<HaEntity, Long> {
    Optional<HaEntity> findByEntityId(String name);
}
