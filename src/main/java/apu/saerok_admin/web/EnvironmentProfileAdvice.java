package apu.saerok_admin.web;

import apu.saerok_admin.web.view.EnvironmentProfileBadge;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class EnvironmentProfileAdvice {

    private final Environment environment;

    public EnvironmentProfileAdvice(Environment environment) {
        this.environment = environment;
    }

    @ModelAttribute("environmentProfileBadge")
    public EnvironmentProfileBadge environmentProfileBadge() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.isEmpty()) {
            activeProfiles = Arrays.asList(environment.getDefaultProfiles());
        }
        if (activeProfiles.contains("local")) {
            return new EnvironmentProfileBadge("로컬", "environment-badge--local");
        }
        if (activeProfiles.contains("dev") || activeProfiles.contains("qa")) {
            return new EnvironmentProfileBadge("개발/QA", "environment-badge--dev");
        }
        if (activeProfiles.contains("prod")) {
            return new EnvironmentProfileBadge("운영", "environment-badge--prod");
        }
        return new EnvironmentProfileBadge("운영", "environment-badge--prod");
    }
}
