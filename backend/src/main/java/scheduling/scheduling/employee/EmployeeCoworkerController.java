package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.auth.CurrentAccountPrincipal;
import scheduling.scheduling.employee.dto.CoworkerResponse;

@RestController
@RequestMapping("/api/employee/coworkers")
@PreAuthorize("hasRole('EMPLOYEE')")
public class EmployeeCoworkerController {

    private final EmployeeCoworkerService employeeCoworkerService;

    public EmployeeCoworkerController(EmployeeCoworkerService employeeCoworkerService) {
        this.employeeCoworkerService = employeeCoworkerService;
    }

    @GetMapping
    ResponseEntity<List<CoworkerResponse>> listCoworkers(
            @AuthenticationPrincipal CurrentAccountPrincipal principal) {
        return ResponseEntity.ok(employeeCoworkerService.listCoworkers(principal));
    }
}
