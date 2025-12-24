package apu.saerok_admin.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 공지사항 에디터(Quill) 이미지 업로드를 위한 Mock 엔드포인트.
 *
 * - 실제 구현에서는 "백엔드 서비스 서버"에서 Presigned URL을 발급하고(S3 PUT URL),
 *   클라이언트는 해당 URL로 업로드한 뒤, 반환받은 public URL을 본문에 삽입한다.
 * - 현재는 어드민 서버 로컬 임시 폴더에 저장하고, /public/mock 경로로 서빙한다.
 */
@RestController
public class NoticeImageMockController {

    private final Path storageDir;
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    public NoticeImageMockController() {
        try {
            this.storageDir = Paths.get(System.getProperty("java.io.tmpdir"), "saerok-admin-mock-notice-images");
            Files.createDirectories(storageDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize mock notice image storage directory.", exception);
        }
    }

    @PostMapping(path = "/public/mock/notices/images/presign", produces = MediaType.APPLICATION_JSON_VALUE)
    public PresignResponse presign(@RequestBody(required = false) PresignRequest request) {
        String contentType = request != null ? request.contentType() : null;
        String filename = request != null ? request.filename() : null;

        String extension = resolveExtension(contentType, filename);
        String objectKey = UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extension : "");

        String uploadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/public/mock/notices/images/upload/")
                .path(objectKey)
                .toUriString();

        String imageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/public/mock/notices/images/")
                .path(objectKey)
                .toUriString();

        return new PresignResponse(uploadUrl, imageUrl, objectKey);
    }

    @PutMapping(path = "/public/mock/notices/images/upload/{objectKey:.+}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> upload(@PathVariable String objectKey, HttpServletRequest request) throws IOException {
        if (!StringUtils.hasText(objectKey)) {
            return ResponseEntity.badRequest().build();
        }

        Path target = storageDir.resolve(objectKey);
        try (InputStream inputStream = request.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String contentType = request.getContentType();
        if (StringUtils.hasText(contentType)) {
            contentTypes.put(objectKey, contentType);
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/public/mock/notices/images/{objectKey:.+}")
    public ResponseEntity<Resource> view(@PathVariable String objectKey) throws IOException {
        if (!StringUtils.hasText(objectKey)) {
            return ResponseEntity.notFound().build();
        }

        Path target = storageDir.resolve(objectKey);
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = contentTypes.get(objectKey);
        if (!StringUtils.hasText(contentType)) {
            contentType = Files.probeContentType(target);
        }
        MediaType mediaType = parseMediaTypeOrFallback(contentType);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noCache())
                .body(new InputStreamResource(Files.newInputStream(target)));
    }

    private static MediaType parseMediaTypeOrFallback(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String resolveExtension(String contentType, String filename) {
        String normalizedContentType = StringUtils.hasText(contentType) ? contentType.toLowerCase(Locale.ROOT) : "";

        if (normalizedContentType.startsWith("image/")) {
            String subtype = normalizedContentType.substring("image/".length());
            if ("jpeg".equals(subtype) || "jpg".equals(subtype)) {
                return "jpg";
            }
            if ("png".equals(subtype) || "webp".equals(subtype) || "gif".equals(subtype)) {
                return subtype;
            }
            if ("svg+xml".equals(subtype)) {
                return "svg";
            }
        }

        if (StringUtils.hasText(filename) && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1);
            ext = ext.trim().toLowerCase(Locale.ROOT);
            if (ext.length() <= 8 && ext.matches("[a-z0-9]+")) {
                return ext;
            }
        }

        return "bin";
    }

    public record PresignRequest(String filename, String contentType) {
    }

    public record PresignResponse(String uploadUrl, String imageUrl, String objectKey) {
    }
}
