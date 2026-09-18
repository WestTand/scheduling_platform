package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;

public class WorkShiftWindowService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record Window(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer workMinutes,
            Boolean transitionShift) {
    }

    public Window derive(LocalDate workDate, ShiftType shiftType) {
        OffsetDateTime startsAt = workDate.atTime(shiftType.getStartTime()).atZone(ZONE).toOffsetDateTime();
        OffsetDateTime endsAt = workDate.atTime(shiftType.getEndTime()).atZone(ZONE).toOffsetDateTime();
        if (!endsAt.isAfter(startsAt)) {
            endsAt = endsAt.plusDays(1);
        }
        int workMinutes = shiftType.getDurationHours()
                .multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        return new Window(startsAt, endsAt, workMinutes, endsAt.toLocalDate().isAfter(startsAt.toLocalDate()));
    }

    public Window resolve(WorkShift workShift) {
        if (workShift.getStartsAt() != null
                && workShift.getEndsAt() != null
                && workShift.getWorkMinutes() != null
                && workShift.getTransitionShift() != null) {
            return new Window(
                    workShift.getStartsAt(),
                    workShift.getEndsAt(),
                    workShift.getWorkMinutes(),
                    workShift.getTransitionShift());
        }
        return derive(workShift.getWorkDate(), workShift.getShiftType());
    }
}
