package apu.saerok_admin.infra.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectionDetailResponse(
        Long collectionId,
        String imageUrl,
        LocalDate discoveredDate,
        Double latitude,
        Double longitude,
        String locationAlias,
        String address,
        String note,
        String accessLevel,
        Long likeCount,
        Long commentCount,
        Boolean isLiked,
        Boolean isMine,
        BirdInfo bird,
        UserInfo user
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BirdInfo(
            Long birdId,
            String koreanName,
            String scientificName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserInfo(
            Long userId,
            String nickname,
            String profileImageUrl
    ) {
    }
}
