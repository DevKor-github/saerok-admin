package apu.saerok_admin.web.view;

import java.util.List;
import org.springframework.util.StringUtils;

public record CurrentAdminProfile(
        String nickname,
        String email,
        String profileImageUrl,
        List<String> roleDescriptions
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
    }

    public boolean hasRoleDescriptions() {
        return !roleDescriptions.isEmpty();
    }

    public static CurrentAdminProfile placeholder() {
        return new CurrentAdminProfile(null, null, null, List.of());
    }
}
