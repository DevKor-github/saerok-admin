package apu.saerok_admin.web.view.ad;

import java.time.OffsetDateTime;
import org.springframework.util.StringUtils;

public record AdListItem(
        Long id,
        String name,
        String memo,
        String imageUrl,
        String contentType,
        String targetUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public boolean hasImage() {
        return StringUtils.hasText(imageUrl);
    }
}
