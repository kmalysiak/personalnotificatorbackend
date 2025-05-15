package pl.kmalysiak.notificator.service;

import jakarta.persistence.Column;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class EnvLister {
    @Value("${spring.datasource.url:NOT_SET}")
    String url;
    @Value("${logging.level.org.springframework.core.env:NOTE_SET}")
    String envLoggingLevel;
    @PostConstruct
    public void test(){
      log.info("Env spring.datasource.url = " + url);
        log.info("Env logging.level.org.springframework.core.env = " + envLoggingLevel);
    }
}
