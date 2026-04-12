package pl.kmalysiak.notificator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@Slf4j
public class Application  {
    public static void main(String[] args) {

        SpringApplication app = new SpringApplication(Application.class);
        app.setBanner((environment, sourceClass, out) -> {
            Properties gitProps = new Properties();

            try (InputStream is = Application.class.getClassLoader().getResourceAsStream("git.properties")) {
                if (is != null) {
                    gitProps.load(is);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            String commitId = gitProps.getProperty("git.commit.id.abbrev", "unknown");
            String commitTime = gitProps.getProperty("git.commit.time", "unknown");
            String buildTime = gitProps.getProperty("git.build.time", "unknown");
            out.println(" ************************NOTIFICATOR***************************");
            out.println(" Commit: " + commitId);
            out.println(" Commit time: " + commitTime);
            out.println(" Build time: " + buildTime);
            out.println(" ************************NOTIFICATOR***************************");
        });


        app.run(args);
    }
}