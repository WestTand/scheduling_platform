package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountEmployeeHashCodeTest {

    @Test
    void accountAndEmployeeHashCodeDoNotRecurseThroughBidirectionalRelation() {
        Employee employee = Employee.builder()
                .employeeCode("EMP001")
                .fullName("Nguyễn Văn An")
                .status("ACTIVE")
                .build();
        Account account = Account.builder()
                .username("emp001@example.com")
                .passwordHash("encoded")
                .role(AccountRole.EMPLOYEE)
                .isActive(true)
                .status(AccountStatus.ACTIVE)
                .employee(employee)
                .build();
        employee.setAccount(account);

        assertThatCode(account::hashCode).doesNotThrowAnyException();
        assertThatCode(employee::hashCode).doesNotThrowAnyException();
    }

    @Test
    void shiftGraphHashCodeDoesNotRecurseThroughCollections() {
        ShiftType shiftType = ShiftType.builder()
                .shiftCode("THONG_TAM")
                .shiftName("Thong tam")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(17, 0))
                .durationHours(new java.math.BigDecimal("8.0"))
                .minStaff(1)
                .maxStaff(20)
                .build();
        SchedulePeriod period = SchedulePeriod.builder()
                .periodName("Tuần mẫu")
                .startDate(LocalDate.of(2026, 9, 21))
                .endDate(LocalDate.of(2026, 9, 27))
                .createdBy(Account.builder().username("manager@example.com").build())
                .build();
        WorkShift workShift = WorkShift.builder()
                .schedulePeriod(period)
                .shiftType(shiftType)
                .workDate(LocalDate.of(2026, 9, 21))
                .requiredStaff(2)
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .employee(Employee.builder().employeeCode("EMP001").fullName("Nguyễn Văn An").build())
                .build();
        Set<ShiftAssignment> assignments = new HashSet<>();
        assignments.add(assignment);
        assignment.setWorkShift(workShift);
        workShift.setShiftAssignments(assignments);
        Set<WorkShift> workShifts = new HashSet<>();
        workShifts.add(workShift);
        shiftType.setWorkShifts(workShifts);

        assertThatCode(workShift::hashCode).doesNotThrowAnyException();
        assertThatCode(assignment::hashCode).doesNotThrowAnyException();
        assertThatCode(shiftType::hashCode).doesNotThrowAnyException();
    }
}
