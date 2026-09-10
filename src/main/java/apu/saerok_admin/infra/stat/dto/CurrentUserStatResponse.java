package apu.saerok_admin.infra.stat.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentUserStatResponse(
        long completedUserCount,
        Map<String, Long> signupSourceCounts,
        Map<String, Long> activePushUserCountsByPlatform
) {
}
