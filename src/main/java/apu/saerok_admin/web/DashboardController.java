package apu.saerok_admin.web;

import apu.saerok_admin.infra.ServiceHealthClient;
import apu.saerok_admin.infra.report.AdminReportClient;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.DashboardMetric;
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

        model.addAttribute("metrics", List.of(reportMetric));
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
