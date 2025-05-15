package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.kmalysiak.notificator.model.HaEntity;

import java.util.Optional;

public interface HaEventRepository extends JpaRepository<HaEntity, Long> {
    Optional<HaEntity> findByEntityId(String name);
}
