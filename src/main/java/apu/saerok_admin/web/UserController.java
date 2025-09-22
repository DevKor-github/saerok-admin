package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.UserActivity;
import apu.saerok_admin.web.view.UserDetail;
import apu.saerok_admin.web.view.UserListItem;
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
@RequestMapping("/users")
public class UserController {

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "사용자 관리");
        model.addAttribute("activeMenu", "users");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("사용자")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastUserAction", "조치 완료", "사용자 상태를 업데이트했습니다."),
                ToastMessage.success("toastUserNotify", "알림 발송", "사용자에게 알림을 전송했습니다.")
        ));
        model.addAttribute("query", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", 14);
        model.addAttribute("totalElements", 512);
        model.addAttribute("statusOptions", List.of("전체", "정상", "휴면", "차단"));
        model.addAttribute("users", List.of(
                new UserListItem(501, "솔바람", "sol@saerok.app", LocalDateTime.now().minusMonths(5), "정상", 48, 2),
                new UserListItem(502, "별빛여행", "star@saerok.app", LocalDateTime.now().minusMonths(3), "정상", 36, 0),
                new UserListItem(503, "느린풍경", "slow@saerok.app", LocalDateTime.now().minusMonths(8), "차단", 12, 7),
                new UserListItem(504, "새벽지기", "dawn@saerok.app", LocalDateTime.now().minusMonths(1), "정상", 18, 1),
                new UserListItem(505, "이끼정원", "moss@saerok.app", LocalDateTime.now().minusMonths(10), "휴면", 5, 0)
        ));
        return "users/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        UserDetail detail = new UserDetail(id, "솔바람", "sol@saerok.app", "010-1234-5678", "정상",
                LocalDateTime.now().minusMonths(5), LocalDateTime.now().minusDays(1), 48, 2,
                List.of(
                        new UserActivity("새록", "초여름 갈대밭 일지", LocalDateTime.now().minusDays(1), "공개"),
                        new UserActivity("댓글", "강변 산책 중 만난 물새들", LocalDateTime.now().minusDays(2), "공개"),
                        new UserActivity("신고", "댓글 신고 #2041", LocalDateTime.now().minusDays(3), "검토중"),
                        new UserActivity("새록", "가을철 맹금류 관찰 노트", LocalDateTime.now().minusDays(7), "공개")
                ));
        model.addAttribute("pageTitle", "사용자 상세");
        model.addAttribute("activeMenu", "users");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("사용자", "/users"),
                Breadcrumb.active("#" + id)));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastUserAction", "조치 완료", "사용자 상태를 업데이트했습니다."),
                ToastMessage.success("toastUserNotify", "알림 발송", "사용자에게 알림을 전송했습니다.")
        ));
        model.addAttribute("detail", detail);
        return "users/detail";
    }
}
