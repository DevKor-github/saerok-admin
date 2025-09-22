package apu.saerok_admin.web;

import apu.saerok_admin.infra.ServiceHealthClient;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.DashboardMetric;
import apu.saerok_admin.web.view.RecentDexEntry;
import apu.saerok_admin.web.view.RecentReport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ServiceHealthClient serviceHealthClient;

    public DashboardController(ServiceHealthClient serviceHealthClient) {
        this.serviceHealthClient = serviceHealthClient;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "대시보드");
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.active("대시보드")));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("serviceHealth", serviceHealthClient.checkHealth());
        model.addAttribute("metrics", List.of(
                new DashboardMetric("오늘 신고", "18건", "bi-flag", "danger", "지난주 대비 +12%"),
                new DashboardMetric("미처리 신고", "7건", "bi-exclamation-triangle", "warning", "48시간 초과 2건"),
                new DashboardMetric("신규 가입자", "42명", "bi-person-plus", "primary", "어제 대비 -5%"),
                new DashboardMetric("최근 알림 발송", "3건", "bi-megaphone", "info", "예약 대기 1건")
        ));
        model.addAttribute("recentReports", List.of(
                new RecentReport(1024, "댓글", "C-481", "욕설 포함", "검토중", LocalDateTime.now().minusHours(1)),
                new RecentReport(1023, "새록", "P-872", "허위 정보", "처리완료", LocalDateTime.now().minusHours(3)),
                new RecentReport(1022, "도감", "D-221", "저작권 침해", "반려", LocalDateTime.now().minusHours(5)),
                new RecentReport(1021, "댓글", "C-477", "광고/도배", "처리완료", LocalDateTime.now().minusDays(1)),
                new RecentReport(1020, "새록", "P-865", "선정성", "대기", LocalDateTime.now().minusDays(1))
        ));
        model.addAttribute("recentDexEntries", List.of(
                new RecentDexEntry(401, "해오라기", "Striated Heron", LocalDate.now().minusDays(1)),
                new RecentDexEntry(399, "황조롱이", "Common Kestrel", LocalDate.now().minusDays(2)),
                new RecentDexEntry(398, "어치", "Eurasian Jay", LocalDate.now().minusDays(3)),
                new RecentDexEntry(397, "개개비", "Oriental Reed Warbler", LocalDate.now().minusDays(4)),
                new RecentDexEntry(396, "쇠백로", "Little Egret", LocalDate.now().minusDays(4))
        ));
        return "dashboard/index";
    }
}
