package scheduling.scheduling.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.ai.dto.AiSchedulingResponseDto;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.ShiftAssignmentRepository;

@Service
public class AiAssignmentPersistenceService {

    private static final String AUTO = "AUTO";
    private static final String MANUAL = "MANUAL";
    private static final String ASSIGNED = "ASSIGNED";

    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public AiAssignmentPersistenceService(ShiftAssignmentRepository shiftAssignmentRepository) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }

    @Transactional
    public ApplyResult apply(
            SchedulePeriod period,
            AiSchedulingPayloadBuilder.PayloadSnapshot snapshot,
            AiSchedulingResponseDto response) {
        try {
            return doApply(period, snapshot, response);
        } catch (AiApplyException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            throw new AiApplyException("AI assignments violate assignment uniqueness", ex);
        } catch (RuntimeException ex) {
            throw new AiApplyException("Failed to persist AI assignments", ex);
        }
    }

    private ApplyResult doApply(
            SchedulePeriod period,
            AiSchedulingPayloadBuilder.PayloadSnapshot snapshot,
            AiSchedulingResponseDto response) {
        List<ShiftAssignment> current = shiftAssignmentRepository.listForPeriod(period.getId());
        List<ShiftAssignment> toDelete = new ArrayList<>();
        List<ShiftAssignment> preserved = new ArrayList<>();
        for (ShiftAssignment assignment : current) {
            if (assignment.getWorkShift() == null
                    || !period.getId().equals(assignment.getWorkShift().getSchedulePeriod().getId())) {
                continue;
            }
            if (AUTO.equals(assignment.getAssignedSource()) && !Boolean.TRUE.equals(assignment.getLocked())) {
                toDelete.add(assignment);
            } else {
                preserved.add(assignment);
            }
        }
        shiftAssignmentRepository.deleteAll(toDelete);

        Map<Long, WorkShift> shiftsById = new HashMap<>();
        for (WorkShift shift : snapshot.workShifts()) {
            shiftsById.put(shift.getId(), shift);
        }
        Set<Long> schedulableEmployeeIds = new HashSet<>();
        for (Employee employee : snapshot.employees()) {
            schedulableEmployeeIds.add(employee.getId());
        }

        Set<String> preservedPairs = new HashSet<>();
        Map<Long, List<ShiftAssignment>> preservedByShift = new HashMap<>();
        Map<Long, List<ShiftAssignment>> preservedByEmployee = new HashMap<>();
        for (ShiftAssignment assignment : preserved) {
            String pair = pairKey(assignment.getEmployee().getId(), assignment.getWorkShift().getId());
            preservedPairs.add(pair);
            preservedByShift.computeIfAbsent(assignment.getWorkShift().getId(), key -> new ArrayList<>())
                    .add(assignment);
            preservedByEmployee.computeIfAbsent(assignment.getEmployee().getId(), key -> new ArrayList<>())
                    .add(assignment);
        }

        List<ShiftAssignment> toInsert = new ArrayList<>();
        Set<String> seenAiPairs = new HashSet<>();
        Map<Long, Integer> newCountByShift = new HashMap<>();
        Map<Long, List<WorkShift>> newShiftsByEmployee = new HashMap<>();
        int skipped = 0;
        for (AiSchedulingResponseDto.AssignmentDto item : response.assignments()) {
            if (item == null || item.employeeId() == null || item.workShiftId() == null) {
                throw new AiApplyException("AI assignment has null item, employee, or shift");
            }
            String pair = pairKey(item.employeeId(), item.workShiftId());
            if (!seenAiPairs.add(pair)) {
                throw new AiApplyException(
                        "Duplicate AI assignment for employee " + item.employeeId()
                                + " and shift " + item.workShiftId());
            }
            if (preservedPairs.contains(pair)) {
                skipped++;
                continue;
            }
            WorkShift shift = shiftsById.get(item.workShiftId());
            if (shift == null) {
                throw new AiApplyException("AI assignment references unknown shift " + item.workShiftId());
            }
            if (!schedulableEmployeeIds.contains(item.employeeId())) {
                throw new AiApplyException("AI assignment references unknown employee " + item.employeeId());
            }
            int preservedCount = preservedByShift.getOrDefault(item.workShiftId(), List.of()).size();
            int newCount = newCountByShift.getOrDefault(item.workShiftId(), 0) + 1;
            if (shift.getRequiredStaff() != null && preservedCount + newCount > shift.getRequiredStaff()) {
                throw new AiApplyException("AI assignments exceed required staff for shift " + item.workShiftId());
            }
            newCountByShift.put(item.workShiftId(), newCount);
            Employee employee = findEmployee(snapshot, item.employeeId());
            checkConflicts(item, shift, employee, snapshot, preservedByEmployee, newShiftsByEmployee);
            newShiftsByEmployee.computeIfAbsent(item.employeeId(), key -> new ArrayList<>()).add(shift);
            toInsert.add(ShiftAssignment.builder()
                    .workShift(shift)
                    .employee(employee)
                    .assignmentStatus(ASSIGNED)
                    .assignedSource(AUTO)
                    .locked(false)
                    .build());
        }

        try {
            shiftAssignmentRepository.saveAll(toInsert);
        } catch (DataIntegrityViolationException ex) {
            throw new AiApplyException("AI assignments violate assignment uniqueness", ex);
        } catch (RuntimeException ex) {
            throw new AiApplyException("Failed to persist AI assignments", ex);
        }
        return new ApplyResult(toInsert.size(), skipped);
    }

    private void checkConflicts(
            AiSchedulingResponseDto.AssignmentDto item,
            WorkShift shift,
            Employee employee,
            AiSchedulingPayloadBuilder.PayloadSnapshot snapshot,
            Map<Long, List<ShiftAssignment>> preservedByEmployee,
            Map<Long, List<WorkShift>> newShiftsByEmployee) {
        LocalDateTime[] candidate = interval(shift);

        List<WorkShift> existingShifts = new ArrayList<>();
        for (ShiftAssignment preserved :
                preservedByEmployee.getOrDefault(item.employeeId(), List.of())) {
            if (preserved.getWorkShift() != null) {
                existingShifts.add(preserved.getWorkShift());
            }
        }
        existingShifts.addAll(newShiftsByEmployee.getOrDefault(item.employeeId(), List.of()));

        for (WorkShift existing : existingShifts) {
            LocalDateTime[] other = interval(existing);
            if (candidate[0].isBefore(other[1]) && other[0].isBefore(candidate[1])) {
                throw new AiApplyException(
                        "AI assignment overlaps existing assignment for employee " + item.employeeId());
            }
        }

        for (var leave : snapshot.leaves()) {
            if (leave.getEmployee() == null || !item.employeeId().equals(leave.getEmployee().getId())) {
                continue;
            }
            if (!shift.getWorkDate().isBefore(leave.getStartDate())
                    && !shift.getWorkDate().isAfter(leave.getEndDate())) {
                throw new AiApplyException(
                        "AI assignment conflicts with approved leave for employee " + item.employeeId());
            }
        }

        int totalMinutes = workMinutes(shift);
        for (WorkShift existing : existingShifts) {
            if (shift.getWorkDate().equals(existing.getWorkDate())) {
                totalMinutes += workMinutes(existing);
            }
        }
        int maxMinutes = snapshot.rules().hardRules().maxWorkHoursPerDay() * 60;
        if (totalMinutes > maxMinutes) {
            throw new AiApplyException(
                    "AI assignments exceed maximum work hours per day for employee " + item.employeeId());
        }
    }

    private static Employee findEmployee(
            AiSchedulingPayloadBuilder.PayloadSnapshot snapshot, Long employeeId) {
        for (Employee employee : snapshot.employees()) {
            if (employeeId.equals(employee.getId())) {
                return employee;
            }
        }
        throw new AiApplyException("AI assignment references unknown employee " + employeeId);
    }

    private static String pairKey(Long employeeId, Long workShiftId) {
        return employeeId + ":" + workShiftId;
    }

    private static LocalDateTime[] interval(WorkShift shift) {
        ShiftType type = shift.getShiftType();
        LocalDateTime start = shift.getWorkDate().atTime(type.getStartTime());
        LocalDateTime end = shift.getWorkDate().atTime(type.getEndTime());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
        }
        return new LocalDateTime[] {start, end};
    }

    private static int workMinutes(WorkShift shift) {
        if (shift.getWorkMinutes() != null) {
            return shift.getWorkMinutes();
        }
        BigDecimal durationHours = shift.getShiftType().getDurationHours();
        return durationHours.multiply(BigDecimal.valueOf(60)).intValue();
    }
}
