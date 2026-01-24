package pl.kmalysiak.notificator;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class Application {

    @Value("${build.number}")
    private String commit;

    @Value("${build.branch}")
    private String branch;

    @Value("${build.timestamp}")
    private String buildTime;
    @PostConstruct
    public void logGit() {
        log.info("Git branch={}, commit={} buildAt={}", branch, commit, buildTime);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}