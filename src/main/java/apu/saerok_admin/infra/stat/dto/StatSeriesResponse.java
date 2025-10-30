package apu.saerok_admin.infra.stat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StatSeriesResponse(List<Series> series) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Series(
            String metric,
            List<Point> points,
            List<ComponentSeries> components
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComponentSeries(
            String key,
            List<Point> points
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(
            LocalDate date,
            Number value
    ) {
    }
}
