package scheduling.scheduling.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import scheduling.scheduling.entity.ShiftType;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-with-at-least-32-characters",
        "app.auth.default-employee-password=configured-default",
})
class ShiftTypeRepositorySearchTest {

    private final String shiftCode = "TEST_SHIFT_" + System.nanoTime();

    @Autowired
    private ShiftTypeRepository shiftTypeRepository;

    @AfterEach
    void tearDown() {
        shiftTypeRepository.findByShiftCode(shiftCode).ifPresent(shiftTypeRepository::delete);
    }

    @Test
    void searchWithNullQueryReturnsShiftTypes() {
        ShiftType shiftType = ShiftType.builder()
                .shiftCode(shiftCode)
                .shiftName("Test shift")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .durationHours(new BigDecimal("8.0"))
                .overnight(false)
                .heavy(false)
                .minStaff(1)
                .maxStaff(20)
                .quotaCategory("REGULAR")
                .status("ACTIVE")
                .applicableWeekdays("MON,TUE,WED,THU,FRI")
                .appliesOnHolidays(false)
                .penaltyWeightMultiplier(new BigDecimal("1.00"))
                .build();
        shiftTypeRepository.saveAndFlush(shiftType);

        assertThat(shiftTypeRepository.search(null, null))
                .extracting(ShiftType::getShiftCode)
                .contains(shiftCode);
    }
}
