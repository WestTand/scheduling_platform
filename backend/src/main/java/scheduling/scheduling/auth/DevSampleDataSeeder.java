package scheduling.scheduling.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.AccountStatus;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.LeaveRequest;
import scheduling.scheduling.entity.Preference;
import scheduling.scheduling.entity.SchedulePeriod;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.ShiftType;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.entity.WorkShift;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SchedulePeriodRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.ShiftTypeRepository;
import scheduling.scheduling.repository.SwapRequestRepository;
import scheduling.scheduling.repository.WorkShiftRepository;

@Component
@Profile("dev")
@Order(30)
public class DevSampleDataSeeder implements ApplicationRunner {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 21);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 27);

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PreferenceRepository preferenceRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final String managerUsername;
    private final String employeePassword;

    public DevSampleDataSeeder(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            ShiftTypeRepository shiftTypeRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            WorkShiftRepository workShiftRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            LeaveRequestRepository leaveRequestRepository,
            PreferenceRepository preferenceRepository,
            SwapRequestRepository swapRequestRepository,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_MANAGER_USERNAME:manager@example.com}") String managerUsername,
            @Value("${SEED_EMPLOYEE_PASSWORD:Employee@123}") String employeePassword) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.workShiftRepository = workShiftRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.preferenceRepository = preferenceRepository;
        this.swapRequestRepository = swapRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.managerUsername = managerUsername;
        this.employeePassword = employeePassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Employee> employees = seedEmployees();
        seedSchedulesAndWorkflow(employees);
    }

    private List<Employee> seedEmployees() {
        return employeeDefinitions().stream()
                .map(this::upsertEmployee)
                .toList();
    }

    private Employee upsertEmployee(EmployeeDefinition definition) {
        Employee employee = employeeRepository.findByEmployeeCode(definition.employeeCode())
                .orElseGet(() -> Employee.builder().employeeCode(definition.employeeCode()).build());
        employee.setFullName(definition.fullName());
        employee.setEmail(definition.email());
        employee.setPhone(definition.phone());
        employee.setStatus("ACTIVE");
        Employee savedEmployee = employeeRepository.save(employee);

        Account account = accountRepository.findByUsername(definition.email())
                .orElseGet(() -> Account.builder().username(definition.email()).build());
        account.setPasswordHash(passwordEncoder.encode(employeePassword));
        account.setRole(AccountRole.EMPLOYEE);
        account.setIsActive(true);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmployee(savedEmployee);
        accountRepository.save(account);

        return savedEmployee;
    }

    private void seedSchedulesAndWorkflow(List<Employee> employees) {
        if (employees.size() < 4 || !StringUtils.hasText(managerUsername)) {
            return;
        }
        Optional<Account> manager = accountRepository.findByUsername(managerUsername);
        Optional<ShiftType> thongTam = shiftTypeRepository.findByShiftCode("THONG_TAM");
        Optional<ShiftType> ngoaiGio1 = shiftTypeRepository.findByShiftCode("NGOAI_GIO_1");
        Optional<ShiftType> trucSang = shiftTypeRepository.findByShiftCode("TRUC_SANG");
        if (manager.isEmpty() || thongTam.isEmpty() || ngoaiGio1.isEmpty() || trucSang.isEmpty()) {
            return;
        }

        SchedulePeriod period = schedulePeriodRepository.findByStartDateAndEndDateAndVersionNo(PERIOD_START, PERIOD_END, 1)
                .orElseGet(() -> schedulePeriodRepository.save(SchedulePeriod.builder()
                        .periodName("Tuần mẫu tháng 09/2026")
                        .startDate(PERIOD_START)
                        .endDate(PERIOD_END)
                        .versionNo(1)
                        .status("DRAFT")
                        .createdBy(manager.get())
                        .build()));

        WorkShift mondayThongTam = upsertWorkShift(period, thongTam.get(), PERIOD_START, 2, "Ca thông tầm mẫu");
        WorkShift tuesdayNgoaiGio = upsertWorkShift(period, ngoaiGio1.get(), PERIOD_START.plusDays(1), 2, "Ca ngoài giờ mẫu");
        WorkShift saturdayTrucSang = upsertWorkShift(period, trucSang.get(), PERIOD_START.plusDays(5), 2, "Ca trực sáng cuối tuần mẫu");

        ShiftAssignment anMonday = upsertAssignment(mondayThongTam, employees.get(0));
        ShiftAssignment binhTuesday = upsertAssignment(tuesdayNgoaiGio, employees.get(1));
        ShiftAssignment chauSaturday = upsertAssignment(saturdayTrucSang, employees.get(2));
        ShiftAssignment dungSaturday = upsertAssignment(saturdayTrucSang, employees.get(3));

        seedLeave(employees.get(0), PERIOD_START.plusDays(2), PERIOD_START.plusDays(2), "PENDING", "Xin nghỉ việc gia đình", null, null);
        seedLeave(employees.get(1), PERIOD_START.plusDays(4), PERIOD_START.plusDays(4), "APPROVED", "Khám sức khỏe", manager.get(), "Đã duyệt nghỉ");
        seedPreference(employees.get(2), thongTam.get(), PERIOD_START.plusDays(3), "WANT", "PENDING", "Muốn làm ca thông tầm");
        seedPreference(employees.get(3), ngoaiGio1.get(), PERIOD_START.plusDays(1), "AVOID", "APPROVED", "Hạn chế làm ngoài giờ");
        seedSwap(anMonday, employees.get(1), null, "PENDING_TARGET", "Nhờ hỗ trợ đổi ca thông tầm");
        seedSwap(chauSaturday, employees.get(3), dungSaturday, "PENDING_APPROVAL", "Đã thống nhất đổi ca trực sáng");
    }

    private WorkShift upsertWorkShift(SchedulePeriod period, ShiftType shiftType, LocalDate date, int requiredStaff, String note) {
        return workShiftRepository.findBySchedulePeriodIdAndWorkDateAndShiftTypeId(period.getId(), date, shiftType.getId())
                .orElseGet(() -> workShiftRepository.save(WorkShift.builder()
                        .schedulePeriod(period)
                        .shiftType(shiftType)
                        .workDate(date)
                        .requiredStaff(requiredStaff)
                        .note(note)
                        .workMinutes(shiftType.getDurationHours().multiply(java.math.BigDecimal.valueOf(60)).intValue())
                        .transitionShift(Boolean.FALSE)
                        .build()));
    }

    private ShiftAssignment upsertAssignment(WorkShift workShift, Employee employee) {
        return shiftAssignmentRepository.findByWorkShiftIdAndEmployeeId(workShift.getId(), employee.getId())
                .orElseGet(() -> shiftAssignmentRepository.save(ShiftAssignment.builder()
                        .workShift(workShift)
                        .employee(employee)
                        .assignmentStatus("ASSIGNED")
                        .assignedSource("MANUAL")
                        .locked(false)
                        .build()));
    }

    private void seedLeave(Employee employee, LocalDate start, LocalDate end, String status, String reason, Account manager, String reviewNote) {
        if (leaveRequestRepository.existsByEmployeeIdAndStartDateAndEndDateAndStatus(employee.getId(), start, end, status)) {
            return;
        }
        leaveRequestRepository.save(LeaveRequest.builder()
                .employee(employee)
                .startDate(start)
                .endDate(end)
                .reason(reason)
                .status(status)
                .reviewedBy(manager)
                .reviewedAt(manager == null ? null : LocalDateTime.now())
                .reviewNote(reviewNote)
                .build());
    }

    private void seedPreference(Employee employee, ShiftType shiftType, LocalDate date, String type, String status, String note) {
        if (preferenceRepository.existsByEmployeeIdAndPreferenceDateAndPreferenceTypeAndStatus(employee.getId(), date, type, status)) {
            return;
        }
        preferenceRepository.save(Preference.builder()
                .employee(employee)
                .shiftType(shiftType)
                .preferenceDate(date)
                .preferenceType(type)
                .priority(1)
                .status(status)
                .note(note)
                .build());
    }

    private void seedSwap(ShiftAssignment requesterAssignment, Employee targetEmployee, ShiftAssignment targetAssignment, String status, String reason) {
        if (swapRequestRepository.existsByRequesterAssignmentIdAndTargetEmployeeIdAndStatus(
                requesterAssignment.getId(), targetEmployee.getId(), status)) {
            return;
        }
        swapRequestRepository.save(SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(targetEmployee)
                .targetAssignment(targetAssignment)
                .status(status)
                .reason(reason)
                .build());
    }

    private static List<EmployeeDefinition> employeeDefinitions() {
        return List.of(
                new EmployeeDefinition("EMP001", "Nguyễn Văn An", "emp001@example.com", "0900000001"),
                new EmployeeDefinition("EMP002", "Trần Thị Bình", "emp002@example.com", "0900000002"),
                new EmployeeDefinition("EMP003", "Lê Minh Châu", "emp003@example.com", "0900000003"),
                new EmployeeDefinition("EMP004", "Phạm Quốc Dũng", "emp004@example.com", "0900000004"));
    }

    private record EmployeeDefinition(String employeeCode, String fullName, String email, String phone) {
    }
}
