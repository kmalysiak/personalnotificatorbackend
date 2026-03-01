package pl.kmalysiak.notificator.service;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.kmalysiak.notificator.dto.HaEntityDto;
import pl.kmalysiak.notificator.model.NotificationData;
import pl.kmalysiak.notificator.model.entity.NotificationTemplateEntity;
import pl.kmalysiak.notificator.repo.NotificationTemplateRepo;
import pl.kmalysiak.notificator.utils.TimeZoneUtils;

import javax.annotation.PostConstruct;
import java.io.StringWriter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationMapper {
    public static final String TEMPLATE_NAME = "dynamic";
    private final NotificationTemplateRepo repo;
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
    private StringTemplateLoader loader;

    @PostConstruct
    public void configureFreemarker() {
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        loader = new StringTemplateLoader();
        cfg.setTemplateLoader(loader);
    }

    public NotificationData getEntFriendlyNameAndMsgContentForNotification(HaEntityDto dto) {

        String statusTempl = Optional
                .ofNullable(dto.getNotificationTemplateId())
                .flatMap(repo::findById)
                .map(NotificationTemplateEntity::getStatusTemplate)
                .orElse(getDefTempl());


        return new NotificationData(
                StringUtils.firstNonBlank(dto.getEntityFriendlyName(), dto.getEntityId(), "nieznany"),
                StringUtils.firstNonBlank(dto.getEntityType(), dto.getEntityFriendlyName(), dto.getEntityId(), "nieznany"),
                render(statusTempl, dto),
                TimeZoneUtils.toEpochSeconds(dto.getCurrTimestamp())

        );
    }

    private String getDefTempl() {
       return ("""
                ${{
                'on'      : 'włączony',
                'off'     : 'wyłączony'
                }[currState]! currState}""");

    }

    //mało wydajne kasowanie z cache, ale na te potrzeby wystarczy
    @SneakyThrows
    public String render(String templateStr, Object context) {
        loader.putTemplate(TEMPLATE_NAME, templateStr);
        cfg.removeTemplateFromCache(TEMPLATE_NAME);
        Template template = cfg.getTemplate(TEMPLATE_NAME);
        StringWriter out = new StringWriter();
        template.process(context, out);
        return out.toString();
    }
}
