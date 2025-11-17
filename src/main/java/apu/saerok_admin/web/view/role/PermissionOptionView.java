package apu.saerok_admin.web.view.role;

import java.util.Locale;
import org.springframework.util.StringUtils;

public record PermissionOptionView(
        String key,
        String description
) {

    public PermissionOptionView {
        key = normalizeKey(key);
        description = StringUtils.hasText(description) ? description.trim() : "";
    }

    public String label() {
        return StringUtils.hasText(description) ? description : key;
    }

    private static String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
