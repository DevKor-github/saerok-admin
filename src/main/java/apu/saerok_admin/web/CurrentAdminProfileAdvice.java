package apu.saerok_admin.web;

import apu.saerok_admin.infra.CurrentAdminClient;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentAdminProfileAdvice {

    private final CurrentAdminClient currentAdminClient;

    public CurrentAdminProfileAdvice(CurrentAdminClient currentAdminClient) {
        this.currentAdminClient = currentAdminClient;
    }

    @ModelAttribute("currentAdminProfile")
    public CurrentAdminProfile currentAdminProfile() {
        return currentAdminClient.fetchCurrentAdminProfile()
                .orElseGet(CurrentAdminProfile::placeholder);
    }
}
