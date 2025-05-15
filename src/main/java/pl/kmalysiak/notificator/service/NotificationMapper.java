package pl.kmalysiak.notificator.service;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.NotificationTemplateEntity;
import pl.kmalysiak.notificator.repo.NotificationTemplateRepository;

import javax.annotation.PostConstruct;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
public class NotificationMapper {
    private final NotificationTemplateRepository repo;
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
    private  StringTemplateLoader loader;
    @PostConstruct
    public void configureFreemarker(){
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        loader = new StringTemplateLoader();
        cfg.setTemplateLoader(loader);
    }

    public Pair<String, String> getNotificationTypeAndStatus(HaEntityDto entity) {
        NotificationTemplateEntity templ = repo.findByEntityId(entity.getEntityId()).orElse(null);

        if (templ == null)
            return null;

        return Pair.of(render(templ.getTypeTemplate(), entity), render(templ.getStatusTemplate(), entity));


    }

    @SneakyThrows
    public String render(String templateStr, Object context) {

        String templateName = "dynamic"; // stała nazwa w loaderze
        loader.putTemplate(templateName, templateStr); // nadpisujemy aktualny template

        Template template = cfg.getTemplate(templateName); // kompilacja nowego template
        StringWriter out = new StringWriter();
        template.process(context, out);

        return out.toString();
    }
}
