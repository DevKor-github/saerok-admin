package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.DexDetail;
import apu.saerok_admin.web.view.DexFormModel;
import apu.saerok_admin.web.view.DexListItem;
import apu.saerok_admin.web.view.ToastMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/dex")
public class DexController {

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String rarity,
                       @RequestParam(required = false) String habitat,
                       @RequestParam(required = false) String stayType,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "도감 관리");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("도감")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastDexDeleted", "삭제 완료", "도감 항목이 삭제 처리되었습니다.")
        ));
        model.addAttribute("query", q);
        model.addAttribute("rarityFilter", rarity);
        model.addAttribute("habitatFilter", habitat);
        model.addAttribute("stayFilter", stayType);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", 8);
        model.addAttribute("totalElements", 146);
        model.addAttribute("rarityOptions", List.of("전체", "COMMON", "UNCOMMON", "RARE", "EPIC"));
        model.addAttribute("habitatOptions", List.of("전체", "하천", "산림", "해안", "도심"));
        model.addAttribute("stayOptions", List.of("전체", "철새", "텃새", "나그네새"));
        model.addAttribute("entries", List.of(
                new DexListItem(401, "해오라기", "Striated Heron", "Butorides striata",
                        "하천", "철새", "RARE", LocalDateTime.now().minusDays(1), List.of("야행성", "대형")),
                new DexListItem(399, "황조롱이", "Common Kestrel", "Falco tinnunculus",
                        "도심", "텃새", "EPIC", LocalDateTime.now().minusDays(2), List.of("맹금류", "맑은 날")),
                new DexListItem(398, "어치", "Eurasian Jay", "Garrulus glandarius",
                        "산림", "텃새", "UNCOMMON", LocalDateTime.now().minusDays(3), List.of("지능형", "소리 흉내")),
                new DexListItem(397, "개개비", "Oriental Reed Warbler", "Acrocephalus orientalis",
                        "습지", "철새", "COMMON", LocalDateTime.now().minusDays(4), List.of("아침 활동", "지저귐")),
                new DexListItem(396, "쇠백로", "Little Egret", "Egretta garzetta",
                        "하천", "나그네새", "UNCOMMON", LocalDateTime.now().minusDays(5), List.of("무리 생활", "시각 사냥"))
        ));
        return "dex/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("pageTitle", "도감 등록");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("도감", "/dex"),
                Breadcrumb.active("신규 등록")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastDexSaved", "임시 저장", "변경사항을 저장했습니다.")
        ));
        model.addAttribute("form", new DexFormModel(null,
                List.of("하천", "산림", "해안", "도심"),
                List.of("철새", "텃새", "나그네새"),
                List.of("COMMON", "UNCOMMON", "RARE", "EPIC")));
        return "dex/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        DexDetail detail = new DexDetail(id, "해오라기", "Striated Heron", "Butorides striata",
                "얕은 물가에서 주로 활동하는 야행성 물새로, 빠른 반사신경으로 사냥을 한다.",
                "https://images.unsplash.com/photo-1503264116251-35a269479413",
                List.of("하천", "습지"), "철새", "RARE", List.of("야행성", "대형"), LocalDateTime.now().minusDays(1));
        model.addAttribute("pageTitle", "도감 상세");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("도감", "/dex"),
                Breadcrumb.active("#" + id)));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastDexDeleted", "삭제 완료", "도감 항목이 삭제 처리되었습니다.")
        ));
        model.addAttribute("detail", detail);
        return "dex/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        DexDetail detail = new DexDetail(id, "해오라기", "Striated Heron", "Butorides striata",
                "얕은 물가에서 주로 활동하는 야행성 물새로, 빠른 반사신경으로 사냥을 한다.",
                "https://images.unsplash.com/photo-1503264116251-35a269479413",
                List.of("하천", "습지"), "철새", "RARE", List.of("야행성", "대형"), LocalDateTime.now().minusDays(1));
        model.addAttribute("pageTitle", "도감 수정");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("도감", "/dex"),
                Breadcrumb.active("수정")
        ));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastDexSaved", "저장 완료", "도감 정보가 저장되었습니다.")
        ));
        model.addAttribute("form", new DexFormModel(detail,
                List.of("하천", "산림", "해안", "도심"),
                List.of("철새", "텃새", "나그네새"),
                List.of("COMMON", "UNCOMMON", "RARE", "EPIC")));
        return "dex/form";
    }
}
