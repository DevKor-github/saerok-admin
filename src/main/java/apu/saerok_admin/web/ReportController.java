package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ReportDetail;
import apu.saerok_admin.web.view.ReportListItem;
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
@RequestMapping("/reports")
public class ReportController {

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "신고 관리");
        model.addAttribute("activeMenu", "reports");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("신고")));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastReportUpdated", "처리 완료", "신고 상태가 업데이트되었습니다."),
                ToastMessage.success("toastReportDeleted", "삭제 완료", "신고가 삭제되었습니다.")
        ));
        model.addAttribute("query", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("typeFilter", type);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", 12);
        model.addAttribute("totalElements", 232);
        model.addAttribute("statusOptions", List.of("전체", "대기", "검토중", "처리완료", "반려"));
        model.addAttribute("typeOptions", List.of("전체", "새록", "댓글", "도감"));
        model.addAttribute("reports", List.of(
                new ReportListItem(2042, "새록", "P-872", "허위 정보", "검토중", LocalDateTime.now().minusHours(2)),
                new ReportListItem(2041, "댓글", "C-481", "욕설 포함", "대기", LocalDateTime.now().minusHours(3)),
                new ReportListItem(2040, "도감", "D-221", "저작권 침해", "반려", LocalDateTime.now().minusHours(5)),
                new ReportListItem(2039, "새록", "P-865", "선정성", "처리완료", LocalDateTime.now().minusDays(1)),
                new ReportListItem(2038, "댓글", "C-477", "광고/도배", "처리완료", LocalDateTime.now().minusDays(1))
        ));
        return "reports/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        ReportDetail detail = new ReportDetail(id, "새록", "검토중", "별빛감시단",
                "허위 정보를 의도적으로 게시한 것으로 보입니다.",
                LocalDateTime.now().minusHours(2),
                "P-872 | 초여름 갈대밭 일지",
                List.of("https://images.unsplash.com/photo-1528825871115-3581a5387919"),
                "초여름 갈대밭 일지",
                "오늘 아침 갈대밭에서 관찰한 새들에 대한 기록입니다. 갈대 사이로...");
        model.addAttribute("pageTitle", "신고 상세");
        model.addAttribute("activeMenu", "reports");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("신고", "/reports"),
                Breadcrumb.active("#" + id)));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastReportUpdated", "처리 완료", "신고 상태가 업데이트되었습니다."),
                ToastMessage.success("toastReportDeleted", "삭제 완료", "신고가 삭제되었습니다.")
        ));
        model.addAttribute("detail", detail);
        return "reports/detail";
    }
}
