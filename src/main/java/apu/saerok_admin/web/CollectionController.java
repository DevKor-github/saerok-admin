package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CollectionDetail;
import apu.saerok_admin.web.view.CollectionListItem;
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
@RequestMapping("/collections")
public class CollectionController {

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "새록 관리");
        model.addAttribute("activeMenu", "collections");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("새록")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastCollectionBulk", "삭제 완료", "선택한 새록을 삭제했습니다."),
                ToastMessage.success("toastCollectionAction", "조치 완료", "새록 상태가 업데이트되었습니다.")
        ));
        model.addAttribute("query", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", 9);
        model.addAttribute("totalElements", 184);
        model.addAttribute("statusOptions", List.of("전체", "공개", "비공개", "차단"));
        model.addAttribute("collections", List.of(
                new CollectionListItem(872, "솔바람", "초여름 갈대밭 풍경과 관찰 기록", "공개", 3, LocalDateTime.now().minusHours(5)),
                new CollectionListItem(871, "이끼정원", "비 내린 후 숲 속의 향기", "공개", 0, LocalDateTime.now().minusHours(8)),
                new CollectionListItem(870, "별빛여행", "야간 탐조 준비물 체크리스트", "비공개", 0, LocalDateTime.now().minusHours(12)),
                new CollectionListItem(869, "새벽지기", "강변 산책 중 만난 물새들", "공개", 1, LocalDateTime.now().minusDays(1)),
                new CollectionListItem(868, "느린풍경", "도심 공원에서 만난 작은 새들", "차단", 5, LocalDateTime.now().minusDays(2))
        ));
        return "collections/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        CollectionDetail detail = new CollectionDetail(id, "솔바람",
                "https://images.unsplash.com/photo-1524504388940-b1c1722653e1",
                "공개", 3, LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(2),
                List.of("갈대밭", "여름", "탐조"),
                "초여름 갈대밭을 찾아 새벽부터 움직였습니다. 부드러운 바람과 함께 ...");
        model.addAttribute("pageTitle", "새록 상세");
        model.addAttribute("activeMenu", "collections");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("새록", "/collections"),
                Breadcrumb.active("#" + id)));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastCollectionAction", "조치 완료", "새록 상태가 업데이트되었습니다."),
                ToastMessage.success("toastCollectionBulk", "삭제 완료", "선택한 새록을 삭제했습니다.")
        ));
        model.addAttribute("detail", detail);
        return "collections/detail";
    }
}
