package scheduling.scheduling.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.SchedulePeriod;

public record AdminSchedulePeriodResponse(
        Long id,
        String periodName,
        LocalDate startDate,
        LocalDate endDate,
        Integer versionNo,
        String status,
        Long createdByAccountId,
        String createdByUsername,
        Long approvedByAccountId,
        String approvedByUsername,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        BigDecimal totalPenalty) {

    public static AdminSchedulePeriodResponse from(SchedulePeriod period) {
        Account createdBy = period.getCreatedBy();
        Account approvedBy = period.getApprovedBy();
        return new AdminSchedulePeriodResponse(
                period.getId(),
                period.getPeriodName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getVersionNo(),
                period.getStatus(),
                createdBy == null ? null : createdBy.getId(),
                createdBy == null ? null : createdBy.getUsername(),
                approvedBy == null ? null : approvedBy.getId(),
                approvedBy == null ? null : approvedBy.getUsername(),
                null,
                period.getPublishedAt(),
                period.getTotalPenalty());
    }
}
