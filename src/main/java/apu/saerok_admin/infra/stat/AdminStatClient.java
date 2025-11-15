package apu.saerok_admin.infra.stat;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminStatClient {

    private static final String[] ADMIN_STATS_SEGMENTS = {"admin", "stats"};
    private static final String SERIES_SEGMENT = "series";

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminStatClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        this.missingPrefixSegments = saerokApiProps.missingPrefixSegments().toArray(new String[0]);
    }

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;

    public StatSeriesResponse fetchSeries(Collection<StatMetric> metrics, LocalDate startDate, LocalDate endDate) {
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("metrics must not be empty");
        }

        StatSeriesResponse response = saerokRestClient.get()
                .uri(uriBuilder -> buildSeriesUri(uriBuilder, metrics, startDate, endDate))
                .retrieve()
                .body(StatSeriesResponse.class);

        if (response == null) {
            throw new IllegalStateException("Empty response from admin stats API");
        }

        return response;
    }

    private URI buildSeriesUri(UriBuilder builder, Collection<StatMetric> metrics, LocalDate startDate, LocalDate endDate) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_STATS_SEGMENTS);
        builder.pathSegment(SERIES_SEGMENT);

        metrics.stream()
                .filter(Objects::nonNull)
                .map(Enum::name)
                .forEach(metric -> builder.queryParam("metric", metric));

        if (startDate != null || endDate != null) {
            builder.queryParam("period", buildPeriodQuery(startDate, endDate));
        }

        return builder.build();
    }

    private String buildPeriodQuery(LocalDate startDate, LocalDate endDate) {
        String startToken = startDate != null ? ISO_DATE.format(startDate) : "";
        String endToken = endDate != null ? ISO_DATE.format(endDate) : "";
        return startToken + "," + endToken;
    }
}
