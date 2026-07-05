package apu.saerok_admin.infra.dex.dto;

import java.util.List;

public record AdminBirdUpsertRequest(
        Name name,
        Taxonomy taxonomy,
        Description description,
        Double bodyLengthCm,
        String nibrUrl,
        String conservationGrade,
        List<String> habitats,
        List<Residency> residencies,
        List<Image> images
) {
    public record Name(
            String koreanName,
            String scientificName,
            String scientificAuthor,
            Integer scientificYear
    ) {
    }

    public record Taxonomy(
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

    public record Description(
            String description,
            String source,
            Boolean isAiGenerated
    ) {
    }

    public record Residency(
            String residencyType,
            String rarity,
            Integer monthBitmask
    ) {
    }

    public record Image(
            String objectKey,
            String originalUrl
    ) {
    }
}
