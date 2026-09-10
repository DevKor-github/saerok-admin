package apu.saerok_admin.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import apu.saerok_admin.web.form.DexUpsertForm;
import java.util.List;
import org.junit.jupiter.api.Test;

class DexFormMapperTest {

    private final DexFormMapper mapper = new DexFormMapper();

    @Test
    void toRequest_convertsSelectedMonthsToBitmask() {
        DexUpsertForm form = validForm();
        form.getResidencies().getFirst().setMonths(List.of(1, 3, 12));

        AdminBirdUpsertRequest request = mapper.toRequest(form);

        assertThat(request.residencies().getFirst().monthBitmask())
                .isEqualTo((1 << 0) | (1 << 2) | (1 << 11));
    }

    @Test
    void toRequest_usesNullBitmaskWhenNoMonthIsSelected() {
        AdminBirdUpsertRequest request = mapper.toRequest(validForm());

        assertThat(request.residencies().getFirst().monthBitmask()).isNull();
    }

    @Test
    void toRequest_rejectsInvalidMonthAndNonHttpUrl() {
        DexUpsertForm invalidMonth = validForm();
        invalidMonth.getResidencies().getFirst().setMonths(List.of(13));
        assertThatThrownBy(() -> mapper.toRequest(invalidMonth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1월부터 12월");

        DexUpsertForm invalidUrl = validForm();
        invalidUrl.setOriginalUrl("javascript:alert(1)");
        assertThatThrownBy(() -> mapper.toRequest(invalidUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http 또는 https");
    }

    private DexUpsertForm validForm() {
        DexUpsertForm form = new DexUpsertForm();
        form.setKoreanName("해오라기");
        form.setScientificName("Butorides striata");
        form.setOrderEng("Pelecaniformes");
        form.setOrderKor("사다새목");
        form.setFamilyEng("Ardeidae");
        form.setFamilyKor("왜가리과");
        form.setGenusEng("Butorides");
        form.setGenusKor("해오라기속");
        form.setSpeciesEng("striata");
        form.setSpeciesKor("해오라기");
        form.setHabitats(List.of("RIVER_LAKE"));
        form.setObjectKey("raw/123.webp");
        form.setOriginalUrl("https://source.example/bird");
        form.getResidencies().getFirst().setResidencyType("RESIDENT");
        form.getResidencies().getFirst().setRarity("COMMON");
        return form;
    }
}
