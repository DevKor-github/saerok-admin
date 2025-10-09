package apu.saerok_admin.infra.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportedCollectionDetailResponse(
        Long reportId,
        CollectionDetailResponse collection,
        CollectionCommentsResponse comments
) {
}
