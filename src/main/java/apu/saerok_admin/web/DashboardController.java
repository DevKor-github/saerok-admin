package apu.saerok_admin.web;

import apu.saerok_admin.infra.ServiceHealthClient;
import apu.saerok_admin.infra.report.AdminReportClient;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.DashboardMetric;
import apu.saerok_admin.web.view.RecentDexEntry;
import apu.saerok_admin.web.view.RecentReport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final ServiceHealthClient serviceHealthClient;
    private final AdminReportClient adminReportClient;

    public DashboardController(ServiceHealthClient serviceHealthClient, AdminReportClient adminReportClient) {
        this.serviceHealthClient = serviceHealthClient;
        this.adminReportClient = adminReportClient;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "대시보드");
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.active("대시보드")));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("serviceHealth", serviceHealthClient.checkHealth());

        ReportCounts reportCounts = fetchReportCounts();
        DashboardMetric reportMetric = reportCounts != null
                ? new DashboardMetric(
                "접수된 신고",
                reportCounts.formattedTotal(),
                "bi-flag",
                "primary",
                reportCounts.subtitle()
        )
                : new DashboardMetric(
                "접수된 신고",
                "정보 없음",
                "bi-flag",
                "secondary",
                "신고 데이터를 불러오지 못했습니다."
        );

        model.addAttribute("metrics", List.of(
                reportMetric,
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

    private ReportCounts fetchReportCounts() {
        try {
            List<ReportedCollectionListResponse.Item> collectionItems = Optional.ofNullable(adminReportClient.listCollectionReports())
                    .map(ReportedCollectionListResponse::items)
                    .orElseGet(List::of);
            List<ReportedCommentListResponse.Item> commentItems = Optional.ofNullable(adminReportClient.listCommentReports())
                    .map(ReportedCommentListResponse::items)
                    .orElseGet(List::of);
            return new ReportCounts(collectionItems.size(), commentItems.size());
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load report counts. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load report counts.", exception);
        }
        return null;
    }

    private record ReportCounts(int collectionCount, int commentCount) {

        int total() {
            return collectionCount + commentCount;
        }

        String formattedTotal() {
            return total() + "건";
        }

        String subtitle() {
            return "새록 " + collectionCount + "건 · 댓글 " + commentCount + "건";
        }
    }
}
