package apu.saerok_admin.web.view;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public record CurrentAdminProfile(
        String nickname,
        String email,
        String profileImageUrl,
        List<String> roleDescriptions,
        List<String> roleCodes
) {

    private static final String DEFAULT_PROFILE_IMAGE_URL =
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1";
    private static final String DEFAULT_NICKNAME = "운영자";
    private static final String DEFAULT_EMAIL = "admin@saerok.app";

    public CurrentAdminProfile {
        nickname = StringUtils.hasText(nickname) ? nickname : DEFAULT_NICKNAME;
        email = StringUtils.hasText(email) ? email : DEFAULT_EMAIL;
        profileImageUrl = StringUtils.hasText(profileImageUrl) ? profileImageUrl : DEFAULT_PROFILE_IMAGE_URL;
        roleDescriptions = roleDescriptions != null ? List.copyOf(roleDescriptions) : List.of();
        roleCodes = normalizeRoleCodes(roleCodes);
    }

    public boolean hasRoleDescriptions() {
        return !roleDescriptions.isEmpty();
    }

    public boolean hasRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String normalized = roleCode.toUpperCase(Locale.ROOT);
        return roleCodes.contains(normalized);
    }

    public boolean isAdminEditor() {
        return hasRole("ADMIN_EDITOR");
    }

    public boolean isAdminViewerOnly() {
        return hasRole("ADMIN_VIEWER") && !isAdminEditor();
    }

    public static CurrentAdminProfile placeholder() {
        return new CurrentAdminProfile(null, null, null, List.of(), List.of());
    }

    private static List<String> normalizeRoleCodes(List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : rawRoles) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            normalized.add(role.toUpperCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }
}
