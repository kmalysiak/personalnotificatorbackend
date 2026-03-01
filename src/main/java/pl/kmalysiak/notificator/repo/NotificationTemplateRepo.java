package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.kmalysiak.notificator.model.entity.NotificationTemplateEntity;

public interface NotificationTemplateRepo extends JpaRepository<NotificationTemplateEntity, Long> {
}
