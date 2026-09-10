package apu.saerok_admin.web.view;

import java.time.OffsetDateTime;
import java.util.List;

public record DexDetail(
        long id,
        String koreanName,
        String scientificName,
        String scientificAuthor,
        Integer scientificYear,
        Taxonomy taxonomy,
        String description,
        String descriptionSource,
        Boolean descriptionAiGenerated,
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
    public DexDetail {
        habitats = habitats == null ? List.of() : List.copyOf(habitats);
        residencies = residencies == null ? List.of() : List.copyOf(residencies);
        seasonsWithRarity = seasonsWithRarity == null ? List.of() : List.copyOf(seasonsWithRarity);
        images = images == null ? List.of() : List.copyOf(images);
    }

    public Image primaryImage() {
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.isThumb()))
                .findFirst()
                .orElseGet(() -> images.isEmpty() ? null : images.getFirst());
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

    public record SeasonWithRarity(String season, String rarity, Integer priority) {
    }

    public record Residency(String residencyType, String rarity, List<Integer> months, boolean usesDefaultMonths) {
        public Residency {
            months = months == null ? List.of() : List.copyOf(months);
        }

        public String monthLabel() {
            return months.isEmpty()
                    ? "선택 월 없음"
                    : months.stream().map(month -> month + "월").reduce((left, right) -> left + ", " + right).orElse("");
        }
    }

    public record Image(String objectKey, String imageUrl, String originalUrl, Integer orderIndex, Boolean isThumb) {
    }
}
