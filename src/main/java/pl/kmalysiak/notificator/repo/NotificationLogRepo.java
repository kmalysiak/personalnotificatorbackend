package pl.kmalysiak.notificator.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.kmalysiak.notificator.model.entity.NotificationLogEntity;

public interface NotificationLogRepo extends JpaRepository<NotificationLogEntity, String> {
    @Modifying
    @Query(value = """
            DELETE FROM ha_notification_log_entity
            WHERE id IN (
                SELECT id
                FROM ha_notification_log_entity
                ORDER BY id ASC
                OFFSET 500
            )
            """, nativeQuery = true)
    void trimTo500();
}
