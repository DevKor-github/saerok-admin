package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CommentListItem;
import apu.saerok_admin.web.view.ToastMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/comments")
public class CommentController {

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "댓글 관리");
        model.addAttribute("activeMenu", "comments");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("댓글")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastCommentBulk", "삭제 완료", "선택한 댓글을 삭제했습니다."),
                ToastMessage.success("toastCommentHide", "조치 완료", "댓글 상태를 변경했습니다.")
        ));
        model.addAttribute("query", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", 15);
        model.addAttribute("totalElements", 312);
        model.addAttribute("statusOptions", List.of("전체", "정상", "숨김", "삭제"));
        model.addAttribute("comments", List.of(
                new CommentListItem(481, "솔바람", 872, "갈대밭의 색감이 정말 아름답네요!", 2, LocalDateTime.now().minusHours(1)),
                new CommentListItem(480, "별빛여행", 865, "정보 감사합니다. 다음 탐조 때 참고할게요.", 0, LocalDateTime.now().minusHours(3)),
                new CommentListItem(479, "새벽지기", 872, "현장 사진이 더 있으면 좋겠어요.", 1, LocalDateTime.now().minusHours(6)),
                new CommentListItem(478, "느린풍경", 860, "도심에서도 이런 새들을 만나다니!", 4, LocalDateTime.now().minusHours(8)),
                new CommentListItem(477, "솔솔바람", 859, "좋은 정보 감사합니다.", 0, LocalDateTime.now().minusHours(10))
        ));
        return "comments/list";
    }
}
