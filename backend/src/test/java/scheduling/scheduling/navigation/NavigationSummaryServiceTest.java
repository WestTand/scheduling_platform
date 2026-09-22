package scheduling.scheduling.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.repository.AccountRepository;
import scheduling.scheduling.repository.LeaveRequestRepository;
import scheduling.scheduling.repository.NotificationRepository;
import scheduling.scheduling.repository.PreferenceRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@ExtendWith(MockitoExtension.class)
class NavigationSummaryServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock LeaveRequestRepository leaveRequestRepository;
    @Mock PreferenceRepository preferenceRepository;
    @Mock SwapRequestRepository swapRequestRepository;
    @InjectMocks NavigationSummaryService service;

    @Test
    void managerSummaryIncludesPendingWorkflowCountsAndUnreadNotifications() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(10L, "manager", AccountRole.MANAGER);
        Account account = Account.builder().username("manager").role(AccountRole.MANAGER).build();
        account.setId(10L);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(leaveRequestRepository.countByStatus("PENDING")).thenReturn(2L);
        when(preferenceRepository.countByStatus("PENDING")).thenReturn(3L);
        when(swapRequestRepository.countByStatus("PENDING_APPROVAL")).thenReturn(4L);
        when(notificationRepository.countUnreadForRecipient(10L)).thenReturn(5L);

        NavigationSummaryResponse response = service.getSummary(principal);

        assertThat(response.pendingLeaveRequests()).isEqualTo(2L);
        assertThat(response.pendingPreferences()).isEqualTo(3L);
        assertThat(response.pendingSwapRequests()).isEqualTo(4L);
        assertThat(response.unreadNotifications()).isEqualTo(5L);
        assertThat(response.pendingSchedules()).isZero();
    }

    @Test
    void employeeSummaryScopesWorkflowCountsToOwnEmployee() {
        CurrentAccountPrincipal principal = new CurrentAccountPrincipal(20L, "employee", AccountRole.EMPLOYEE);
        Employee employee = Employee.builder().employeeCode("NV001").fullName("Nhan vien").status("ACTIVE").build();
        employee.setId(7L);
        Account account = Account.builder().username("employee").role(AccountRole.EMPLOYEE).employee(employee).build();
        account.setId(20L);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(account));
        when(leaveRequestRepository.countByEmployeeIdAndStatus(7L, "PENDING")).thenReturn(1L);
        when(preferenceRepository.countByEmployeeIdAndStatus(7L, "PENDING")).thenReturn(2L);
        when(swapRequestRepository.countPendingForEmployee(7L)).thenReturn(3L);
        when(notificationRepository.countUnreadForRecipient(20L)).thenReturn(4L);

        NavigationSummaryResponse response = service.getSummary(principal);

        assertThat(response.pendingLeaveRequests()).isEqualTo(1L);
        assertThat(response.pendingPreferences()).isEqualTo(2L);
        assertThat(response.pendingSwapRequests()).isEqualTo(3L);
        assertThat(response.unreadNotifications()).isEqualTo(4L);
        assertThat(response.pendingSchedules()).isZero();
    }
}
