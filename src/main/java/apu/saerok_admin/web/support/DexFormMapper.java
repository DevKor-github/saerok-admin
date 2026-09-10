package apu.saerok_admin.web.support;

import apu.saerok_admin.infra.dex.dto.AdminBirdDetailResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import apu.saerok_admin.web.form.DexUpsertForm;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DexFormMapper {

    public AdminBirdUpsertRequest toRequest(DexUpsertForm form) {
        if (form == null || !StringUtils.hasText(form.getKoreanName()) || !StringUtils.hasText(form.getScientificName())) {
            throw new IllegalArgumentException("국문명과 학명을 입력해 주세요.");
        }
        if (!allHaveText(
                form.getPhylumEng(), form.getPhylumKor(), form.getClassEng(), form.getClassKor(),
                form.getOrderEng(), form.getOrderKor(), form.getFamilyEng(), form.getFamilyKor(),
                form.getGenusEng(), form.getGenusKor(), form.getSpeciesEng(), form.getSpeciesKor())) {
            throw new IllegalArgumentException("분류 정보를 모두 입력해 주세요.");
        }
        if (!StringUtils.hasText(form.getConservationGrade())) {
            throw new IllegalArgumentException("보호등급을 선택해 주세요.");
        }
        if (form.getHabitats() == null || form.getHabitats().stream().noneMatch(StringUtils::hasText)) {
            throw new IllegalArgumentException("서식지를 하나 이상 선택해 주세요.");
        }
        if (!StringUtils.hasText(form.getObjectKey()) || !StringUtils.hasText(form.getOriginalUrl())) {
            throw new IllegalArgumentException("대표 이미지와 원본 출처 URL을 입력해 주세요.");
        }

        String originalUrl = requireHttpUrl(form.getOriginalUrl(), "이미지 원본 출처 URL은 http 또는 https URL이어야 합니다.");
        String nibrUrl = optionalHttpUrl(form.getNibrUrl(), "NIBR URL은 http 또는 https URL이어야 합니다.");
        List<AdminBirdUpsertRequest.Residency> residencies = mapResidencies(form.getResidencies());
        if (residencies.isEmpty()) {
            throw new IllegalArgumentException("체류/희귀도 정보를 하나 이상 입력해 주세요.");
        }

        return new AdminBirdUpsertRequest(
                new AdminBirdUpsertRequest.Name(
                        form.getKoreanName().trim(),
                        form.getScientificName().trim(),
                        trimToNull(form.getScientificAuthor()),
                        parseOptionalInteger(form.getScientificYear(), "학명 명명년도는 숫자로 입력해 주세요.")
                ),
                new AdminBirdUpsertRequest.Taxonomy(
                        form.getPhylumEng().trim(),
                        form.getPhylumKor().trim(),
                        form.getClassEng().trim(),
                        form.getClassKor().trim(),
                        form.getOrderEng().trim(),
                        form.getOrderKor().trim(),
                        form.getFamilyEng().trim(),
                        form.getFamilyKor().trim(),
                        form.getGenusEng().trim(),
                        form.getGenusKor().trim(),
                        form.getSpeciesEng().trim(),
                        form.getSpeciesKor().trim()
                ),
                new AdminBirdUpsertRequest.Description(
                        trimToNull(form.getDescription()),
                        trimToNull(form.getDescriptionSource()),
                        form.isDescriptionAiGenerated()
                ),
                parseOptionalPositiveDouble(form.getBodyLengthCm()),
                nibrUrl,
                form.getConservationGrade().trim(),
                form.getHabitats().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList(),
                residencies,
                List.of(new AdminBirdUpsertRequest.Image(form.getObjectKey().trim(), originalUrl))
        );
    }

    public DexUpsertForm toForm(AdminBirdDetailResponse detail) {
        DexUpsertForm form = new DexUpsertForm();

        AdminBirdDetailResponse.BirdName name = detail.name();
        if (name != null) {
            form.setKoreanName(name.koreanName());
            form.setScientificName(name.scientificName());
            form.setScientificAuthor(name.scientificAuthor());
            form.setScientificYear(name.scientificYear() == null ? null : String.valueOf(name.scientificYear()));
        }

        AdminBirdDetailResponse.BirdTaxonomy taxonomy = detail.taxonomy();
        if (taxonomy != null) {
            form.setPhylumEng(taxonomy.phylumEng());
            form.setPhylumKor(taxonomy.phylumKor());
            form.setClassEng(taxonomy.classEng());
            form.setClassKor(taxonomy.classKor());
            form.setOrderEng(taxonomy.orderEng());
            form.setOrderKor(taxonomy.orderKor());
            form.setFamilyEng(taxonomy.familyEng());
            form.setFamilyKor(taxonomy.familyKor());
            form.setGenusEng(taxonomy.genusEng());
            form.setGenusKor(taxonomy.genusKor());
            form.setSpeciesEng(taxonomy.speciesEng());
            form.setSpeciesKor(taxonomy.speciesKor());
        }

        AdminBirdDetailResponse.BirdDescription description = detail.description();
        if (description != null) {
            form.setDescription(description.description());
            form.setDescriptionSource(description.source());
            form.setDescriptionAiGenerated(Boolean.TRUE.equals(description.isAiGenerated()));
        }

        form.setBodyLengthCm(detail.bodyLengthCm() == null ? null : stripTrailingZero(detail.bodyLengthCm()));
        form.setNibrUrl(detail.nibrUrl());
        if (detail.conservationGrade() != null) {
            form.setConservationGrade(detail.conservationGrade());
        }
        form.setHabitats(detail.habitats() == null ? new ArrayList<>() : new ArrayList<>(detail.habitats()));

        List<DexUpsertForm.ResidencyRow> rows = new ArrayList<>();
        if (detail.residencies() != null) {
            for (AdminBirdDetailResponse.Residency residency : detail.residencies()) {
                DexUpsertForm.ResidencyRow row = new DexUpsertForm.ResidencyRow();
                row.setResidencyType(residency.residencyType());
                row.setRarity(residency.rarity());
                row.setMonths(monthsFromBitmask(residency.monthBitmask()));
                rows.add(row);
            }
        }
        form.setResidencies(rows);
        form.ensureResidencyRow();

        AdminBirdDetailResponse.Image image = primaryImage(detail.images());
        if (image != null) {
            form.setObjectKey(image.objectKey());
            form.setOriginalUrl(image.originalUrl());
        }

        return form;
    }

    private AdminBirdDetailResponse.Image primaryImage(List<AdminBirdDetailResponse.Image> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(image -> Boolean.TRUE.equals(image.isThumb()))
                .findFirst()
                .orElse(images.get(0));
    }

    private List<Integer> monthsFromBitmask(Integer bitmask) {
        if (bitmask == null) {
            return new ArrayList<>();
        }
        return IntStream.rangeClosed(1, 12)
                .filter(month -> (bitmask & (1 << (month - 1))) != 0)
                .boxed()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private String stripTrailingZero(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private List<AdminBirdUpsertRequest.Residency> mapResidencies(List<DexUpsertForm.ResidencyRow> rows) {
        if (rows == null) {
            return List.of();
        }
        List<AdminBirdUpsertRequest.Residency> result = new ArrayList<>();
        for (DexUpsertForm.ResidencyRow row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("체류/희귀도 정보를 입력해 주세요.");
            }
            boolean hasType = StringUtils.hasText(row.getResidencyType());
            boolean hasRarity = StringUtils.hasText(row.getRarity());
            boolean hasMonths = row.getMonths() != null && !row.getMonths().isEmpty();
            if (!hasType && !hasRarity && !hasMonths) {
                continue;
            }
            if (!hasType || !hasRarity) {
                throw new IllegalArgumentException("체류 형태와 희귀도를 모두 선택해 주세요.");
            }
            result.add(new AdminBirdUpsertRequest.Residency(
                    row.getResidencyType().trim(),
                    row.getRarity().trim(),
                    toMonthBitmask(row.getMonths())
            ));
        }
        return result;
    }

    private Integer toMonthBitmask(List<Integer> months) {
        if (months == null || months.isEmpty()) {
            return null;
        }
        int bitmask = 0;
        for (Integer month : months) {
            if (month == null || month < 1 || month > 12) {
                throw new IllegalArgumentException("월은 1월부터 12월까지만 선택할 수 있습니다.");
            }
            bitmask |= 1 << (month - 1);
        }
        return bitmask;
    }

    private Integer parseOptionalInteger(String value, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private Double parseOptionalPositiveDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("체장은 0보다 커야 합니다.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("체장은 숫자로 입력해 주세요.");
        }
    }

    private String optionalHttpUrl(String value, String message) {
        return StringUtils.hasText(value) ? requireHttpUrl(value, message) : null;
    }

    private String requireHttpUrl(String value, String message) {
        String trimmed = value.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost())) {
                return trimmed;
            }
        } catch (URISyntaxException ignored) {
            // Fall through to the form error below.
        }
        throw new IllegalArgumentException(message);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean allHaveText(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }
}
