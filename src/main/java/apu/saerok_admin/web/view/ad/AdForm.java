package apu.saerok_admin.web.view.ad;

import org.springframework.util.StringUtils;

public record AdForm(
        Long id,
        String name,
        String targetUrl,
        String memo,
        String objectKey,
        String contentType,
        String imageUrl
) {

    public boolean hasExistingImage() {
        return StringUtils.hasText(imageUrl);
    }

    public boolean hasUploadedImage() {
        return StringUtils.hasText(objectKey) && StringUtils.hasText(contentType);
    }
}
