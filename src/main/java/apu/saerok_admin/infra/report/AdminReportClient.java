package apu.saerok_admin.infra.report;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.report.dto.ReportedCollectionDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminReportClient {

    private final RestClient saerokRestClient;
    private final List<String> missingPrefixSegments;

    public AdminReportClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments();
    }

    public ReportedCollectionListResponse listCollectionReports() {
        ReportedCollectionListResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "collections"))
                .retrieve()
                .body(ReportedCollectionListResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response when fetching collection reports");
        }
        return response;
    }

    public ReportedCommentListResponse listCommentReports() {
        ReportedCommentListResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "comments"))
                .retrieve()
                .body(ReportedCommentListResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response when fetching comment reports");
        }
        return response;
    }

    public ReportedCollectionDetailResponse getCollectionReportDetail(Long reportId) {
        ReportedCollectionDetailResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "collections", reportId.toString()))
                .retrieve()
                .body(ReportedCollectionDetailResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response when fetching collection report detail");
        }
        return response;
    }

    public ReportedCommentDetailResponse getCommentReportDetail(Long reportId) {
        ReportedCommentDetailResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "comments", reportId.toString()))
                .retrieve()
                .body(ReportedCommentDetailResponse.class);
        if (response == null) {
            throw new IllegalStateException("Empty response when fetching comment report detail");
        }
        return response;
    }

    public void ignoreCollectionReport(Long reportId) {
        saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "collections", reportId.toString(), "ignore"))
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteCollectionByReport(Long reportId) {
        saerokRestClient.delete()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "collections", reportId.toString()))
                .retrieve()
                .toBodilessEntity();
    }

    public void ignoreCommentReport(Long reportId) {
        saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "comments", reportId.toString(), "ignore"))
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteCommentByReport(Long reportId) {
        saerokRestClient.delete()
                .uri(uriBuilder -> buildUri(uriBuilder, "admin", "reports", "comments", reportId.toString()))
                .retrieve()
                .toBodilessEntity();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (!missingPrefixSegments.isEmpty()) {
            builder.pathSegment(missingPrefixSegments.toArray(String[]::new));
        }
        builder.pathSegment(segments);
        return builder.build();
    }
}
