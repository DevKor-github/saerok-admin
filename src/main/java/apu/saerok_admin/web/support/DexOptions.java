package apu.saerok_admin.web.support;

import apu.saerok_admin.web.view.DexFormModel;
import apu.saerok_admin.web.view.DexOption;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DexOptions {

    private static final List<DexOption> HABITATS = List.of(
            new DexOption("MUDFLAT", "갯벌"),
            new DexOption("FARMLAND", "경작지/들판"),
            new DexOption("FOREST", "산림/계곡"),
            new DexOption("MARINE", "해양"),
            new DexOption("RESIDENTIAL", "거주지역"),
            new DexOption("PLAINS_FOREST", "평지숲"),
            new DexOption("RIVER_LAKE", "하천/호수"),
            new DexOption("ARTIFICIAL", "인공시설"),
            new DexOption("CAVE", "동굴"),
            new DexOption("WETLAND", "습지"),
            new DexOption("OTHERS", "기타")
    );
    private static final List<DexOption> RESIDENCIES = List.of(
            new DexOption("RESIDENT", "텃새"),
            new DexOption("SUMMER", "여름철새"),
            new DexOption("WINTER", "겨울철새"),
            new DexOption("PASSAGE", "나그네새"),
            new DexOption("VAGRANT", "길잃은새")
    );
    private static final List<DexOption> RARITIES = List.of(
            new DexOption("COMMON", "흔함"),
            new DexOption("RARE", "드묾"),
            new DexOption("UNSPECIFIED", "미상")
    );
    private static final List<DexOption> CONSERVATION_GRADES = List.of(
            new DexOption("NONE", "해당 없음"),
            new DexOption("GRADE_I", "멸종위기 야생생물 I급"),
            new DexOption("GRADE_II", "멸종위기 야생생물 II급")
    );
    private static final List<DexOption> SEASONS = List.of(
            new DexOption("SPRING", "봄"),
            new DexOption("SUMMER", "여름"),
            new DexOption("AUTUMN", "가을"),
            new DexOption("WINTER", "겨울")
    );

    public DexFormModel formModel() {
        return new DexFormModel(
                HABITATS,
                RESIDENCIES,
                RARITIES,
                CONSERVATION_GRADES,
                IntStream.rangeClosed(1, 12).boxed().toList()
        );
    }

    public List<String> habitatLabels(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(value -> label(HABITATS, value)).toList();
    }

    public String residencyLabel(String value) {
        return label(RESIDENCIES, value);
    }

    public String rarityLabel(String value) {
        return label(RARITIES, value);
    }

    public String conservationGradeLabel(String value) {
        return label(CONSERVATION_GRADES, value);
    }

    public String seasonLabel(String value) {
        return label(SEASONS, value);
    }

    private String label(List<DexOption> options, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return options.stream()
                .filter(option -> option.value().equals(value))
                .map(DexOption::label)
                .findFirst()
                .orElse(value);
    }
}
