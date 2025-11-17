package apu.saerok_admin.web.view.role;

import java.util.Locale;
import org.springframework.util.StringUtils;

public record RoleDisplay(
        Long id,
        String code,
        String displayName,
        String description,
        boolean builtin
) {

    public RoleDisplay {
        code = normalizeCode(code);
        displayName = StringUtils.hasText(displayName) ? displayName.trim() : code;
        description = StringUtils.hasText(description) ? description.trim() : "";
    }

    public String label() {
        return StringUtils.hasText(displayName) ? displayName : code;
    }

    public boolean hasDescription() {
        return StringUtils.hasText(description);
    }

    private static String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
