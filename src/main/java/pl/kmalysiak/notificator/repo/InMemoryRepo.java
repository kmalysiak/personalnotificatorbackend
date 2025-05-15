package pl.kmalysiak.notificator.repo;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryRepo {
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    public String get(String key){
        return tokenStore.get(key);
    }

    public void put(String key, String val){
        tokenStore.put(key, val);
    }
}
