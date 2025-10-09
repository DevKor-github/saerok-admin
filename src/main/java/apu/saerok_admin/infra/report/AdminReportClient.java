package apu.saerok_admin.infra.report;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.report.dto.ReportedCollectionDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import java.net.URI;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminReportClient {

    private static final String[] ADMIN_REPORTS_SEGMENTS = {"admin", "reports"};
    private static final String[] ADMIN_REPORT_SEGMENTS = {"admin", "report"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminReportClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments().toArray(new String[0]);
    }

    public ReportedCollectionListResponse listCollectionReports() {
        return getWithFallback(ReportedCollectionListResponse.class,
                "collection reports",
                concat(ADMIN_REPORTS_SEGMENTS, "collections"),
                concat(ADMIN_REPORT_SEGMENTS, "collections"));
    }

    public ReportedCommentListResponse listCommentReports() {
        return getWithFallback(ReportedCommentListResponse.class,
                "comment reports",
                concat(ADMIN_REPORTS_SEGMENTS, "comments"),
                concat(ADMIN_REPORT_SEGMENTS, "comments"));
    }

    public ReportedCollectionDetailResponse getCollectionReportDetail(Long reportId) {
        String id = reportId.toString();
        return getWithFallback(ReportedCollectionDetailResponse.class,
                "collection report detail",
                concat(ADMIN_REPORTS_SEGMENTS, "collections", id),
                concat(ADMIN_REPORT_SEGMENTS, "collections", id));
    }

    public ReportedCommentDetailResponse getCommentReportDetail(Long reportId) {
        String id = reportId.toString();
        return getWithFallback(ReportedCommentDetailResponse.class,
                "comment report detail",
                concat(ADMIN_REPORTS_SEGMENTS, "comments", id),
                concat(ADMIN_REPORT_SEGMENTS, "comments", id));
    }

    public void ignoreCollectionReport(Long reportId) {
        String id = reportId.toString();
        postWithFallback("ignore collection report",
                concat(ADMIN_REPORTS_SEGMENTS, "collections", id, "ignore"),
                concat(ADMIN_REPORT_SEGMENTS, "collections", id, "ignore"));
    }

    public void deleteCollectionByReport(Long reportId) {
        String id = reportId.toString();
        deleteWithFallback("delete collection by report",
                concat(ADMIN_REPORTS_SEGMENTS, "collections", id),
                concat(ADMIN_REPORT_SEGMENTS, "collections", id));
    }

    public void ignoreCommentReport(Long reportId) {
        String id = reportId.toString();
        postWithFallback("ignore comment report",
                concat(ADMIN_REPORTS_SEGMENTS, "comments", id, "ignore"),
                concat(ADMIN_REPORT_SEGMENTS, "comments", id, "ignore"));
    }

    public void deleteCommentByReport(Long reportId) {
        String id = reportId.toString();
        deleteWithFallback("delete comment by report",
                concat(ADMIN_REPORTS_SEGMENTS, "comments", id),
                concat(ADMIN_REPORT_SEGMENTS, "comments", id));
    }

    private <T> T getWithFallback(Class<T> responseType,
                                  String errorContext,
                                  String[] primarySegments,
                                  String[]... fallbackSegments) {
        RestClientResponseException notFound = null;
        for (String[] segments : candidates(primarySegments, fallbackSegments)) {
            try {
                T response = saerokRestClient.get()
                        .uri(uriBuilder -> buildUri(uriBuilder, segments))
                        .retrieve()
                        .body(responseType);
                if (response == null) {
                    throw new IllegalStateException("Empty response when fetching " + errorContext);
                }
                return response;
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    notFound = ex;
                    continue;
                }
                throw ex;
            }
        }
        if (notFound != null) {
            throw notFound;
        }
        throw new IllegalStateException("Empty response when fetching " + errorContext);
    }

    private void postWithFallback(String errorContext,
                                  String[] primarySegments,
                                  String[]... fallbackSegments) {
        RestClientResponseException notFound = null;
        for (String[] segments : candidates(primarySegments, fallbackSegments)) {
            try {
                saerokRestClient.post()
                        .uri(uriBuilder -> buildUri(uriBuilder, segments))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    notFound = ex;
                    continue;
                }
                throw ex;
            }
        }
        if (notFound != null) {
            throw notFound;
        }
        throw new IllegalStateException("Failed to " + errorContext + ": endpoint not found");
    }

    private void deleteWithFallback(String errorContext,
                                    String[] primarySegments,
                                    String[]... fallbackSegments) {
        RestClientResponseException notFound = null;
        for (String[] segments : candidates(primarySegments, fallbackSegments)) {
            try {
                saerokRestClient.delete()
                        .uri(uriBuilder -> buildUri(uriBuilder, segments))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    notFound = ex;
                    continue;
                }
                throw ex;
            }
        }
        if (notFound != null) {
            throw notFound;
        }
        throw new IllegalStateException("Failed to " + errorContext + ": endpoint not found");
    }

    private String[][] candidates(String[] primarySegments, String[]... fallbackSegments) {
        String[][] result = new String[1 + fallbackSegments.length][];
        result[0] = primarySegments;
        System.arraycopy(fallbackSegments, 0, result, 1, fallbackSegments.length);
        return result;
    }

    private String[] concat(String[] prefix, String... suffix) {
        String[] result = Arrays.copyOf(prefix, prefix.length + suffix.length);
        System.arraycopy(suffix, 0, result, prefix.length, suffix.length);
        return result;
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(segments);
        return builder.build();
    }
}
