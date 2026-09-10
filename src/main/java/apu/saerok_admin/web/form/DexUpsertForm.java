package apu.saerok_admin.web.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DexUpsertForm {

    private String koreanName;
    private String scientificName;
    private String scientificAuthor;
    private String scientificYear;
    private String bodyLengthCm;
    private String nibrUrl;
    private String conservationGrade = "NONE";
    private String phylumEng = "Chordata";
    private String phylumKor = "척삭동물문";
    private String classEng = "Aves";
    private String classKor = "조강";
    private String orderEng;
    private String orderKor;
    private String familyEng;
    private String familyKor;
    private String genusEng;
    private String genusKor;
    private String speciesEng;
    private String speciesKor;
    private List<String> habitats = new ArrayList<>();
    private List<ResidencyRow> residencies = new ArrayList<>(List.of(new ResidencyRow()));
    private String objectKey;
    private String contentType;
    private String originalUrl;
    private String description;
    private String descriptionSource;
    private boolean descriptionAiGenerated;

    public void ensureResidencyRow() {
        if (residencies == null || residencies.isEmpty()) {
            residencies = new ArrayList<>(List.of(new ResidencyRow()));
        }
    }

    @Getter
    @Setter
    public static class ResidencyRow {
        private String residencyType;
        private String rarity;
        private List<Integer> months = new ArrayList<>();
    }
}
