package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CreateSwapRequestRequest;
import scheduling.scheduling.employee.dto.RespondSwapRequestRequest;
import scheduling.scheduling.employee.dto.SwapRequestResponse;
import scheduling.scheduling.entity.Employee;
import scheduling.scheduling.entity.ShiftAssignment;
import scheduling.scheduling.entity.SwapRequest;
import scheduling.scheduling.repository.EmployeeRepository;
import scheduling.scheduling.repository.ShiftAssignmentRepository;
import scheduling.scheduling.repository.SwapRequestRepository;

@Service
public class EmployeeSwapRequestService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String PENDING_TARGET = "PENDING_TARGET";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";

    private final EmployeeAccess employeeAccess;
    private final SwapRequestRepository swapRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeSwapRequestService(
            EmployeeAccess employeeAccess,
            SwapRequestRepository swapRequestRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            EmployeeRepository employeeRepository) {
        this.employeeAccess = employeeAccess;
        this.swapRequestRepository = swapRequestRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public SwapRequestResponse createSwapRequest(
            CurrentAccountPrincipal principal, CreateSwapRequestRequest request) {
        if (request == null) {
            throw invalidRequest();
        }
        Employee requester = employeeAccess.requireActiveEmployeeForUpdate(principal);
        ShiftAssignment requesterAssignment = shiftAssignmentRepository.findDetailById(request.requesterAssignmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!requester.getId().equals(requesterAssignment.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        requirePublished(requesterAssignment);
        Employee target = employeeRepository.findById(request.targetEmployeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee is not active");
        }
        if (requester.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot target yourself");
        }
        ShiftAssignment targetAssignment = null;
        if (request.targetAssignmentId() != null) {
            targetAssignment = shiftAssignmentRepository.findDetailById(request.targetAssignmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
            if (!target.getId().equals(targetAssignment.getEmployee().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Target assignment does not belong to target employee");
            }
            if (requesterAssignment.getId().equals(targetAssignment.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Target assignment must differ from requester assignment");
            }
            requirePublished(targetAssignment);
        }
        SwapRequest swapRequest = SwapRequest.builder()
                .requesterAssignment(requesterAssignment)
                .targetEmployee(target)
                .targetAssignment(targetAssignment)
                .reason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null)
                .status(PENDING_TARGET)
                .build();
        return SwapRequestResponse.from(swapRequestRepository.save(swapRequest));
    }

    @Transactional(readOnly = true)
    public List<SwapRequestResponse> listSwapRequests(CurrentAccountPrincipal principal, String status) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Transactional
    public SwapRequestResponse cancelSwapRequest(CurrentAccountPrincipal principal, Long swapRequestId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Transactional
    public SwapRequestResponse respondSwapRequest(
            CurrentAccountPrincipal principal, Long swapRequestId, RespondSwapRequestRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    private static void requirePublished(ShiftAssignment assignment) {
        if (!PUBLISHED.equals(assignment.getWorkShift().getSchedulePeriod().getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Assignment is not in a published schedule");
        }
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid swap request");
    }
}
