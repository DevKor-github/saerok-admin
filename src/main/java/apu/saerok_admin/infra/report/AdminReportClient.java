package apu.saerok_admin.infra.report;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.report.dto.AdminDeleteReasonRequest;
import apu.saerok_admin.infra.report.dto.ReportedCollectionDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import java.net.URI;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminReportClient {

    private static final String[] ADMIN_REPORTS_SEGMENTS = {"admin", "reports"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminReportClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments().toArray(new String[0]);
    }

    public ReportedCollectionListResponse listCollectionReports() {
        return get(ReportedCollectionListResponse.class, "collections");
    }

    public ReportedCommentListResponse listCommentReports() {
        return get(ReportedCommentListResponse.class, "comments");
    }

    public ReportedCollectionDetailResponse getCollectionReportDetail(Long reportId) {
        String id = reportId.toString();
        return get(ReportedCollectionDetailResponse.class, "collections", id);
    }

    public ReportedCommentDetailResponse getCommentReportDetail(Long reportId) {
        String id = reportId.toString();
        return get(ReportedCommentDetailResponse.class, "comments", id);
    }

    public void ignoreCollectionReport(Long reportId) {
        String id = reportId.toString();
        post("collections", id, "ignore");
    }

    public void deleteCollectionByReport(Long reportId, String reason) {
        String id = reportId.toString();
        deleteWithBody(new AdminDeleteReasonRequest(reason), "collections", id);
    }

    public void ignoreCommentReport(Long reportId) {
        String id = reportId.toString();
        post("comments", id, "ignore");
    }

    public void deleteCommentByReport(Long reportId, String reason) {
        String id = reportId.toString();
        deleteWithBody(new AdminDeleteReasonRequest(reason), "comments", id);
    }

    private <T> T get(Class<T> responseType, String... segments) {
        T response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin report API");
        }
        return response;
    }

    private void post(String... segments) {
        saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .toBodilessEntity();
    }

    private void deleteWithBody(Object body, String... segments) {
        saerokRestClient.method(HttpMethod.DELETE)
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_REPORTS_SEGMENTS);
        builder.pathSegment(segments);
        return builder.build();
    }
}
