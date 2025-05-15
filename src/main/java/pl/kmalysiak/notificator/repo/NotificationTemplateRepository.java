package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.kmalysiak.notificator.model.NotificationTemplateEntity;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {
    Optional<NotificationTemplateEntity> findByEntityId(String entityId);
}
