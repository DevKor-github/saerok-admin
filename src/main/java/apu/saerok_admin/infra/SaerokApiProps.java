package apu.saerok_admin.infra;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "saerok.api")
public record SaerokApiProps(String baseUrl, String prefix) {

    public SaerokApiProps {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("saerok.api.base-url must not be empty");
        }
        prefix = normalizePrefix(prefix);
    }

    public List<String> missingPrefixSegments() {
        List<String> prefixSegments = splitSegments(prefix);
        if (prefixSegments.isEmpty()) {
            return List.of();
        }
        List<String> basePathSegments = splitSegments(URI.create(baseUrl).getPath());
        if (endsWith(basePathSegments, prefixSegments)) {
            return List.of();
        }
        return prefixSegments;
    }

    private static boolean endsWith(List<String> base, List<String> suffix) {
        if (suffix.size() > base.size()) {
            return false;
        }
        for (int i = 0; i < suffix.size(); i++) {
            String baseSegment = base.get(base.size() - suffix.size() + i);
            if (!baseSegment.equals(suffix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String trimmed = prefix.trim();
        if ("/".equals(trimmed)) {
            return "";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> splitSegments(String path) {
        if (!StringUtils.hasText(path)) {
            return List.of();
        }
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (!StringUtils.hasText(trimmed)) {
            return List.of();
        }
        String[] tokens = trimmed.split("/");
        List<String> segments = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (StringUtils.hasText(token)) {
                segments.add(token);
            }
        }
        return Collections.unmodifiableList(segments);
    }
}

