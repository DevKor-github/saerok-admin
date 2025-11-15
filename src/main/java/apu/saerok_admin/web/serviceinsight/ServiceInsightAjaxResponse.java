package apu.saerok_admin.web.serviceinsight;

import apu.saerok_admin.web.view.ServiceInsightViewModel;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record ServiceInsightAjaxResponse(
        @JsonProperty("viewModel") ServiceInsightViewModel viewModel,
        @JsonProperty("selectedRange") String selectedRange,
        @JsonProperty("customRangeActive") boolean customRangeActive,
        @JsonProperty("startDate") LocalDate startDate,
        @JsonProperty("endDate") LocalDate endDate,
        @JsonProperty("error") boolean error
) {
}
