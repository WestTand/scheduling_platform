package scheduling.scheduling.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;

import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.repository.ShiftTypeRepository;

class StandardShiftTypeSeederTest {

    private ShiftTypeRepository shiftTypeRepository;
    private StandardShiftTypeSeeder seeder;

    private static final List<String> CODES = List.of(
            "THONG_TAM", "NGOAI_GIO_1", "NGOAI_GIO_2", "TRUC_SANG", "TRUC_CHIEU", "TRUC_DEM");

    private static final Map<String, String> NAMES = Map.of(
            "THONG_TAM", "Thong tam",
            "NGOAI_GIO_1", "Ngoai gio 1",
            "NGOAI_GIO_2", "Ngoai gio 2",
            "TRUC_SANG", "Truc sang",
            "TRUC_CHIEU", "Truc chieu",
            "TRUC_DEM", "Truc dem");

    @BeforeEach
    void setUp() {
        shiftTypeRepository = mock(ShiftTypeRepository.class);
        seeder = new StandardShiftTypeSeeder(shiftTypeRepository);
        when(shiftTypeRepository.saveAndFlush(any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubAllMissing() {
        for (String code : CODES) {
            when(shiftTypeRepository.findByShiftCode(code)).thenReturn(Optional.empty());
            when(shiftTypeRepository.findByShiftName(NAMES.get(code))).thenReturn(Optional.empty());
        }
    }

    @Test
    void createsSixMissingRowsByShiftCode() throws Exception {
        stubAllMissing();

        seeder.run(mock(ApplicationArguments.class));

        ArgumentCaptor<ShiftType> captor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, times(6)).saveAndFlush(captor.capture());
        List<String> savedCodes = captor.getAllValues().stream().map(ShiftType::getShiftCode).toList();
        assertThat(savedCodes).containsExactlyInAnyOrderElementsOf(CODES);
        for (String code : CODES) {
            verify(shiftTypeRepository, times(1)).findByShiftCode(code);
        }
    }

    @Test
    void updatesExistingRowByShiftCode() throws Exception {
        ShiftType existing = ShiftType.builder()
                .shiftName("Thong tam")
                .shiftCode("THONG_TAM")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .durationHours(new BigDecimal("1.0"))
                .overnight(true)
                .heavy(false)
                .minStaff(5)
                .maxStaff(6)
                .quotaCategory("REGULAR")
                .status("INACTIVE")
                .applicableWeekdays("SAT,SUN")
                .appliesOnHolidays(true)
                .penaltyWeightMultiplier(new BigDecimal("9.99"))
                .build();
        when(shiftTypeRepository.findByShiftCode("THONG_TAM")).thenReturn(Optional.of(existing));
        when(shiftTypeRepository.findByShiftName("Thong tam")).thenReturn(Optional.empty());
        for (String code : CODES) {
            if (!code.equals("THONG_TAM")) {
                when(shiftTypeRepository.findByShiftCode(code)).thenReturn(Optional.empty());
                when(shiftTypeRepository.findByShiftName(NAMES.get(code))).thenReturn(Optional.empty());
            }
        }

        seeder.run(mock(ApplicationArguments.class));

        assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(existing.getDurationHours()).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(existing.getOvernight()).isFalse();
        assertThat(existing.getHeavy()).isTrue();
        assertThat(existing.getApplicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI");
        assertThat(existing.getAppliesOnHolidays()).isFalse();
        assertThat(existing.getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(existing.getQuotaCategory()).isEqualTo("BATHING");
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getMinStaff()).isEqualTo(1);
        assertThat(existing.getMaxStaff()).isEqualTo(20);
        verify(shiftTypeRepository).saveAndFlush(existing);
    }

    @Test
    void backfillsLegacyRowByShiftNameWhenShiftCodeNull() throws Exception {
        ShiftType legacy = ShiftType.builder()
                .shiftName("Thong tam")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .durationHours(new BigDecimal("1.0"))
                .overnight(true)
                .heavy(false)
                .minStaff(5)
                .maxStaff(6)
                .quotaCategory("REGULAR")
                .status("INACTIVE")
                .applicableWeekdays("SAT,SUN")
                .appliesOnHolidays(true)
                .penaltyWeightMultiplier(new BigDecimal("9.99"))
                .build();
        when(shiftTypeRepository.findByShiftCode("THONG_TAM")).thenReturn(Optional.empty());
        when(shiftTypeRepository.findByShiftName("Thong tam")).thenReturn(Optional.of(legacy));
        for (String code : CODES) {
            if (!code.equals("THONG_TAM")) {
                when(shiftTypeRepository.findByShiftCode(code)).thenReturn(Optional.empty());
                when(shiftTypeRepository.findByShiftName(NAMES.get(code))).thenReturn(Optional.empty());
            }
        }

        seeder.run(mock(ApplicationArguments.class));

        assertThat(legacy.getShiftCode()).isEqualTo("THONG_TAM");
        assertThat(legacy.getShiftName()).isEqualTo("Thong tam");
        assertThat(legacy.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(legacy.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(legacy.getQuotaCategory()).isEqualTo("BATHING");
        assertThat(legacy.getStatus()).isEqualTo("ACTIVE");
        verify(shiftTypeRepository).saveAndFlush(legacy);
    }

    @Test
    void seedsAllRequiredColumns() throws Exception {
        stubAllMissing();

        seeder.run(mock(ApplicationArguments.class));

        ArgumentCaptor<ShiftType> captor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, times(6)).saveAndFlush(captor.capture());
        Map<String, ShiftType> byCode = new HashMap<>();
        for (ShiftType saved : captor.getAllValues()) {
            byCode.put(saved.getShiftCode(), saved);
            assertThat(saved.getShiftName()).isNotBlank();
            assertThat(saved.getStartTime()).isNotNull();
            assertThat(saved.getEndTime()).isNotNull();
            assertThat(saved.getDurationHours()).isNotNull();
            assertThat(saved.getOvernight()).isNotNull();
            assertThat(saved.getHeavy()).isNotNull();
            assertThat(saved.getMinStaff()).isNotNull();
            assertThat(saved.getMaxStaff()).isNotNull();
            assertThat(saved.getQuotaCategory()).isNotBlank();
            assertThat(saved.getStatus()).isNotBlank();
            assertThat(saved.getApplicableWeekdays()).isNotNull();
            assertThat(saved.getAppliesOnHolidays()).isNotNull();
            assertThat(saved.getPenaltyWeightMultiplier()).isNotNull();
        }

        ShiftType thongTam = byCode.get("THONG_TAM");
        assertThat(thongTam.getShiftName()).isEqualTo("Thong tam");
        assertThat(thongTam.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(thongTam.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(thongTam.getDurationHours()).isEqualByComparingTo(new BigDecimal("8.0"));
        assertThat(thongTam.getOvernight()).isFalse();
        assertThat(thongTam.getHeavy()).isTrue();
        assertThat(thongTam.getApplicableWeekdays()).isEqualTo("MON,TUE,WED,THU,FRI");
        assertThat(thongTam.getAppliesOnHolidays()).isFalse();
        assertThat(thongTam.getQuotaCategory()).isEqualTo("BATHING");
        assertThat(thongTam.getMinStaff()).isEqualTo(1);
        assertThat(thongTam.getMaxStaff()).isEqualTo(20);
        assertThat(thongTam.getStatus()).isEqualTo("ACTIVE");

        ShiftType trucDem = byCode.get("TRUC_DEM");
        assertThat(trucDem.getShiftName()).isEqualTo("Truc dem");
        assertThat(trucDem.getStartTime()).isEqualTo(LocalTime.of(0, 0));
        assertThat(trucDem.getEndTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(trucDem.getQuotaCategory()).isEqualTo("ON_CALL");
        assertThat(trucDem.getApplicableWeekdays()).isEqualTo("SAT,SUN");
        assertThat(trucDem.getAppliesOnHolidays()).isTrue();
    }

    @Test
    void seedsExactMultipliers() throws Exception {
        stubAllMissing();

        seeder.run(mock(ApplicationArguments.class));

        ArgumentCaptor<ShiftType> captor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, times(6)).saveAndFlush(captor.capture());
        Map<String, ShiftType> byCode = new HashMap<>();
        for (ShiftType saved : captor.getAllValues()) {
            byCode.put(saved.getShiftCode(), saved);
        }
        assertThat(byCode.get("THONG_TAM").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(byCode.get("NGOAI_GIO_1").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(byCode.get("NGOAI_GIO_2").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.50"));
        assertThat(byCode.get("TRUC_SANG").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(byCode.get("TRUC_CHIEU").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.20"));
        assertThat(byCode.get("TRUC_DEM").getPenaltyWeightMultiplier()).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    void isIdempotentOnRepeatedRun() throws Exception {
        stubAllMissing();

        seeder.run(mock(ApplicationArguments.class));
        ArgumentCaptor<ShiftType> firstCaptor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, times(6)).saveAndFlush(firstCaptor.capture());
        List<ShiftType> firstSaved = List.copyOf(firstCaptor.getAllValues());

        for (ShiftType saved : firstSaved) {
            when(shiftTypeRepository.findByShiftCode(saved.getShiftCode())).thenReturn(Optional.of(saved));
            when(shiftTypeRepository.findByShiftName(saved.getShiftName())).thenReturn(Optional.of(saved));
        }
        clearInvocations(shiftTypeRepository);
        when(shiftTypeRepository.saveAndFlush(any(ShiftType.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(mock(ApplicationArguments.class));

        verify(shiftTypeRepository, times(6)).saveAndFlush(any(ShiftType.class));
        // No duplicates: every save after second run reuses the same six codes
        ArgumentCaptor<ShiftType> allCaptor = ArgumentCaptor.forClass(ShiftType.class);
        verify(shiftTypeRepository, times(6)).saveAndFlush(allCaptor.capture());
        assertThat(allCaptor.getAllValues())
                .extracting(ShiftType::getShiftCode)
                .containsExactlyInAnyOrderElementsOf(CODES);
    }

    @Test
    void skipsLegacyRowWhenShiftCodeAndShiftNameCollideOnDifferentRows() throws Exception {
        ShiftType byCodeRow = ShiftType.builder()
                .shiftName("Old thong tam")
                .shiftCode("THONG_TAM")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .durationHours(new BigDecimal("1.0"))
                .overnight(true)
                .heavy(false)
                .minStaff(5)
                .maxStaff(6)
                .quotaCategory("REGULAR")
                .status("INACTIVE")
                .applicableWeekdays("SAT,SUN")
                .appliesOnHolidays(true)
                .penaltyWeightMultiplier(new BigDecimal("9.99"))
                .build();
        byCodeRow.setId(1L);
        ShiftType legacyRow = ShiftType.builder()
                .shiftName("Thong tam")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .durationHours(new BigDecimal("1.0"))
                .overnight(true)
                .heavy(false)
                .minStaff(5)
                .maxStaff(6)
                .quotaCategory("REGULAR")
                .status("INACTIVE")
                .applicableWeekdays("SAT,SUN")
                .appliesOnHolidays(true)
                .penaltyWeightMultiplier(new BigDecimal("9.99"))
                .build();
        legacyRow.setId(2L);

        when(shiftTypeRepository.findByShiftCode("THONG_TAM")).thenReturn(Optional.of(byCodeRow));
        when(shiftTypeRepository.findByShiftName("Thong tam")).thenReturn(Optional.of(legacyRow));
        for (String code : CODES) {
            if (!code.equals("THONG_TAM")) {
                when(shiftTypeRepository.findByShiftCode(code)).thenReturn(Optional.empty());
                when(shiftTypeRepository.findByShiftName(NAMES.get(code))).thenReturn(Optional.empty());
            }
        }

        seeder.run(mock(ApplicationArguments.class));

        assertThat(byCodeRow.getShiftName()).isEqualTo("Old thong tam");
        assertThat(byCodeRow.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(byCodeRow.getQuotaCategory()).isEqualTo("BATHING");
        assertThat(byCodeRow.getStatus()).isEqualTo("ACTIVE");
        verify(shiftTypeRepository).saveAndFlush(byCodeRow);
        verify(shiftTypeRepository, never()).saveAndFlush(legacyRow);
        assertThat(legacyRow.getShiftCode()).isNull();
    }

    @Test
    void doesNotAbortBootOnConcurrentInsertConflict() throws Exception {
        stubAllMissing();
        when(shiftTypeRepository.saveAndFlush(any(ShiftType.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatNoException().isThrownBy(() -> seeder.run(mock(ApplicationArguments.class)));

        verify(shiftTypeRepository, times(6)).saveAndFlush(any(ShiftType.class));
    }
}
