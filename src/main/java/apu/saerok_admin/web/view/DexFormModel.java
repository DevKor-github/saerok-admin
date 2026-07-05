package apu.saerok_admin.web.view;

import java.util.List;

public record DexFormModel(
        List<DexOption> habitatOptions,
        List<DexOption> residencyOptions,
        List<DexOption> rarityOptions,
        List<DexOption> conservationGradeOptions,
        List<Integer> months
) {
}
