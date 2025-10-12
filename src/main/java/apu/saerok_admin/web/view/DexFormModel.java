package apu.saerok_admin.web.view;

import java.util.List;

public record DexFormModel(DexDetail detail, List<String> habitatOptions, List<String> stayOptions,
                           List<String> rarityOptions) {
}
