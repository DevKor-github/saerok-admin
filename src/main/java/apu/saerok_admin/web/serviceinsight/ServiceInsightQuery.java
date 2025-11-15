package apu.saerok_admin.web.serviceinsight;

import java.time.LocalDate;

public record ServiceInsightQuery(LocalDate startDate, LocalDate endDate) {

    public static ServiceInsightQuery all() {
        return new ServiceInsightQuery(null, null);
    }

    public boolean hasRange() {
        return startDate != null && endDate != null;
    }
}
