package scheduling.scheduling.admin;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Component
public class StandardShiftTypeSeeder implements ApplicationRunner {

    private final ShiftTypeRepository shiftTypeRepository;

    public StandardShiftTypeSeeder(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (CanonicalShift definition : definitions()) {
            shiftTypeRepository.findByShiftCode(definition.shiftCode)
                    .map(existing -> {
                        applyCanonical(existing, definition);
                        return shiftTypeRepository.save(existing);
                    })
                    .orElseGet(() -> shiftTypeRepository.findByShiftName(definition.shiftName)
                            .map(legacy -> {
                                applyCanonical(legacy, definition);
                                return shiftTypeRepository.save(legacy);
                            })
                            .orElseGet(() -> shiftTypeRepository.save(newShiftType(definition))));
        }
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
            return 1;
        }

        int maxStaff() {
            return 20;
        }

        String status() {
            return "ACTIVE";
        }
    }
}
