package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Component
public class StandardShiftTypeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StandardShiftTypeSeeder.class);

    private static final int DEFAULT_MIN_STAFF = 1;
    private static final int DEFAULT_MAX_STAFF = 20;
    private static final String DEFAULT_STATUS = "ACTIVE";

    private final ShiftTypeRepository shiftTypeRepository;

    public StandardShiftTypeSeeder(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (CanonicalShift definition : definitions()) {
            seedOne(definition);
        }
    }

    private void seedOne(CanonicalShift definition) {
        Optional<ShiftType> byCode = shiftTypeRepository.findByShiftCode(definition.shiftCode);
        if (byCode.isPresent()) {
            ShiftType existing = byCode.get();
            Optional<ShiftType> byName = shiftTypeRepository.findByShiftName(definition.shiftName);
            if (byName.isPresent() && isDifferentRow(existing, byName.get())) {
                log.warn(
                        "Skipping legacy backfill for shiftName '{}': already used by different row id={} "
                                + "while updating shiftCode '{}' id={}",
                        definition.shiftName, byName.get().getId(),
                        definition.shiftCode, existing.getId());
            }
            applyCanonical(existing, definition);
            saveSafely(existing, definition, "updated");
        } else {
            Optional<ShiftType> legacy = shiftTypeRepository.findByShiftName(definition.shiftName);
            if (legacy.isPresent()) {
                ShiftType target = legacy.get();
                applyCanonical(target, definition);
                saveSafely(target, definition, "backfilled");
            } else {
                saveSafely(newShiftType(definition), definition, "created");
            }
        }
    }

    private void saveSafely(ShiftType entity, CanonicalShift definition, String outcome) {
        try {
            shiftTypeRepository.save(entity);
            log.info("Seeded canonical shift {}: shiftCode={}", outcome, definition.shiftCode);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent seeding conflict for shiftCode {}: {}. Re-fetching existing row.",
                    definition.shiftCode, ex.getMessage());
            shiftTypeRepository.findByShiftCode(definition.shiftCode)
                    .ifPresent(existing -> log.warn("Found existing shiftCode {} after conflict, skipping create",
                            existing.getShiftCode()));
        }
    }

    private static boolean isDifferentRow(ShiftType left, ShiftType right) {
        if (left == right) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return !Objects.equals(left.getId(), right.getId());
        }
        return left != right;
    }

    private static void applyCanonical(ShiftType target, CanonicalShift definition) {
        target.setShiftCode(definition.shiftCode);
        target.setShiftName(definition.shiftName);
        target.setStartTime(definition.startTime);
        target.setEndTime(definition.endTime);
        target.setDurationHours(definition.durationHours);
        target.setOvernight(definition.overnight);
        target.setHeavy(definition.heavy);
        target.setMinStaff(definition.minStaff());
        target.setMaxStaff(definition.maxStaff());
        target.setQuotaCategory(definition.quotaCategory);
        target.setStatus(definition.status());
        target.setApplicableWeekdays(definition.applicableWeekdays);
        target.setAppliesOnHolidays(definition.appliesOnHolidays);
        target.setPenaltyWeightMultiplier(definition.penaltyWeightMultiplier);
    }

    private static ShiftType newShiftType(CanonicalShift definition) {
        ShiftType shiftType = ShiftType.builder()
                .shiftCode(definition.shiftCode)
                .shiftName(definition.shiftName)
                .startTime(definition.startTime)
                .endTime(definition.endTime)
                .durationHours(definition.durationHours)
                .overnight(definition.overnight)
                .heavy(definition.heavy)
                .minStaff(definition.minStaff())
                .maxStaff(definition.maxStaff())
                .quotaCategory(definition.quotaCategory)
                .status(definition.status())
                .applicableWeekdays(definition.applicableWeekdays)
                .appliesOnHolidays(definition.appliesOnHolidays)
                .penaltyWeightMultiplier(definition.penaltyWeightMultiplier)
                .build();
        return shiftType;
    }

    private static List<CanonicalShift> definitions() {
        return List.of(
                new CanonicalShift("THONG_TAM", "Thong tam", "BATHING",
                        LocalTime.of(8, 0), LocalTime.of(17, 0), new BigDecimal("8.0"),
                        false, true, "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.00")),
                new CanonicalShift("NGOAI_GIO_1", "Ngoai gio 1", "OVERTIME",
                        LocalTime.of(17, 0), LocalTime.of(0, 30), new BigDecimal("7.5"),
                        true, false, "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.20")),
                new CanonicalShift("NGOAI_GIO_2", "Ngoai gio 2", "OVERTIME",
                        LocalTime.of(0, 30), LocalTime.of(8, 0), new BigDecimal("7.5"),
                        false, true, "MON,TUE,WED,THU,FRI", false, new BigDecimal("1.50")),
                new CanonicalShift("TRUC_SANG", "Truc sang", "ON_CALL",
                        LocalTime.of(8, 0), LocalTime.of(16, 0), new BigDecimal("8.0"),
                        false, false, "SAT,SUN", true, new BigDecimal("1.00")),
                new CanonicalShift("TRUC_CHIEU", "Truc chieu", "ON_CALL",
                        LocalTime.of(16, 0), LocalTime.of(0, 0), new BigDecimal("8.0"),
                        true, false, "SAT,SUN", true, new BigDecimal("1.20")),
                new CanonicalShift("TRUC_DEM", "Truc dem", "ON_CALL",
                        LocalTime.of(0, 0), LocalTime.of(8, 0), new BigDecimal("8.0"),
                        false, true, "SAT,SUN", true, new BigDecimal("1.50")));
    }

    private record CanonicalShift(
            String shiftCode,
            String shiftName,
            String quotaCategory,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal durationHours,
            boolean overnight,
            boolean heavy,
            String applicableWeekdays,
            boolean appliesOnHolidays,
            BigDecimal penaltyWeightMultiplier) {
        int minStaff() {
            return DEFAULT_MIN_STAFF;
        }

        int maxStaff() {
            return DEFAULT_MAX_STAFF;
        }

        String status() {
            return DEFAULT_STATUS;
        }
    }
}
