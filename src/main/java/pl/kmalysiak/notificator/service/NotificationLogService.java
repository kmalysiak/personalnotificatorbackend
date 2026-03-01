package pl.kmalysiak.notificator.service;


import com.github.f4b6a3.ulid.UlidCreator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kmalysiak.notificator.dto.NotificationLogDto;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.entity.NotificationLogEntity;
import pl.kmalysiak.notificator.repo.NotificationLogRepo;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

import static pl.kmalysiak.notificator.utils.TimeZoneUtils.epochSecondsToLocalDateTime;

@Service
@Data
@Slf4j
public class NotificationLogService {

    private final NotificationLogRepo repo;


    @Cacheable("log")
    public List<NotificationLogDto> getLog(String user) {
        return repo.findAll().stream()
                .filter(i -> "all".equals(i.getReceiver()) || i.getReceiver().contains(user))
                .map(i -> CustomObjectMapper.mapObject(i, NotificationLogDto.class))
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "log", allEntries = true)
    public void addToLog(NotificationData nd, String receiver) {
        NotificationLogEntity ent = new NotificationLogEntity();
        ent.setId(UlidCreator.getMonotonicUlid().toString());
        ent.setMsg(nd.msg());
        ent.setReceiver(receiver);
        ent.setRawTimestamp(nd.timestamp());
        ent.setEntityFriendlyName(nd.entityFriendlyName());
        ent.setType(nd.type());
        ent.setTimestamp(epochSecondsToLocalDateTime(nd.timestamp()));
        repo.save(ent);
        repo.trimTo500();
    }


    @Scheduled(fixedRate = 30 * 60 * 1000)
    @CacheEvict(value = "log", allEntries = true)
    public void evictCache() {
        log.info("Cache evict cron");
    }

}
