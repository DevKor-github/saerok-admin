package apu.saerok_admin.infra.dex.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminBirdDetailResponse(
        Long id,
        BirdName name,
        BirdTaxonomy taxonomy,
        BirdDescription description,
        Double bodyLengthCm,
        String nibrUrl,
        String conservationGrade,
        List<String> habitats,
        List<Residency> residencies,
        List<SeasonWithRarity> seasonsWithRarity,
        List<Image> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record BirdName(
            String koreanName,
            String scientificName,
            String scientificAuthor,
            Integer scientificYear
    ) {
    }

    public record BirdTaxonomy(
            String phylumEng,
            String phylumKor,
            String classEng,
            String classKor,
            String orderEng,
            String orderKor,
            String familyEng,
            String familyKor,
            String genusEng,
            String genusKor,
            String speciesEng,
            String speciesKor
    ) {
    }

    public record BirdDescription(
            String description,
            String source,
            Boolean isAiGenerated
    ) {
    }

    public record SeasonWithRarity(
            String season,
            String rarity,
            Integer priority
    ) {
    }

    public record Residency(
            String residencyType,
            String rarity,
            Integer monthBitmask,
            Integer effectiveMonthBitmask
    ) {
    }

    public record Image(
            String objectKey,
            String imageUrl,
            String originalUrl,
            Integer orderIndex,
            Boolean isThumb
    ) {
    }
}
