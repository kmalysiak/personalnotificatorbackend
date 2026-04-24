package pl.kmalysiak.notificator.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.entity.HaEntity;
import pl.kmalysiak.notificator.rabbit.model.EventNotification;
import pl.kmalysiak.notificator.repo.HaEntityRepo;
import pl.kmalysiak.notificator.utils.CustomObjectMapper;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HaEventService {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HaEntityRepo repo;

    public static String addValue(String jsonColumn, String newValue, int maxSize) {
        List<String> list;

        try {
            if (jsonColumn == null || jsonColumn.isEmpty()) {
                list = new ArrayList<>();
            } else {
                list = mapper.readValue(jsonColumn, new TypeReference<List<String>>() {
                });
            }
        } catch (Exception e) {
            // fallback if parsing fails
            list = new ArrayList<>();
        }

        // Add new value
        list.add(newValue);

        // Ensure max size
        while (list.size() > maxSize) {
            list.remove(0); // remove oldest
        }

        try {
            return mapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON list", e);
        }
    }

    @Transactional
    public HaEntityDto updateHaEntityStateOnHaEvent(EventNotification en) {
        Optional<HaEntity> rk = repo.findByEntityId(en.getEntityId());
        HaEntity dbRk;

        if (rk.isPresent()) {
            dbRk = rk.get();
        } else {
            dbRk = new HaEntity();
            dbRk.setEntityId(en.getEntityId());
            dbRk.setNotifyMobile(false);
            dbRk.setReceivedCount(0L);
            dbRk.setNotifyFreq("P100Y");
            dbRk.setPayloadHistCount(20);


        }

        dbRk.setPrevReceived(dbRk.getCurrReceived());
        dbRk.setCurrReceived(TimeZoneUtils.getLocalDateTimeNow());

        dbRk.setPrevTimestamp(dbRk.getCurrTimestamp());
        dbRk.setCurrTimestamp(en.getTimestamp());

        dbRk.setPrevState(dbRk.getCurrState());
        dbRk.setCurrState(en.getState());

        dbRk.setPrevUnit(dbRk.getCurrUnit());
        dbRk.setCurrUnit(en.getUnitOfMeasurement());

        dbRk.setPayload(addValue(dbRk.getPayload(), en.getPayload(), dbRk.getPayloadHistCount()));

        dbRk.setReceivedCount(dbRk.getReceivedCount() + 1);

        return CustomObjectMapper.mapObject(repo.save(dbRk), HaEntityDto.class);
    }

    public void updateLastNotified(String entId) {
        Optional<HaEntity> rk = repo.findByEntityId(entId);
        if (rk.isPresent()) {
            rk.get().setLastNotified(TimeZoneUtils.getLocalDateTimeNow());
            repo.save(rk.get());
        }
    }

    public boolean isRoutingKeyExist(String name) {
        return !repo.findByEntityId(name).isEmpty();
    }

    public List<HaEntity> getAllRoutingKeys() {
        return repo.findAll();
    }
}

