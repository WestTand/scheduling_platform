package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import scheduling.scheduling.employee.dto.EmployeeShiftTypeResponse;

@RestController
@RequestMapping("/api/employee/shift-types")
@PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','ADMIN')")
public class EmployeeShiftTypeController {

    private final EmployeeShiftTypeService employeeShiftTypeService;

    public EmployeeShiftTypeController(EmployeeShiftTypeService employeeShiftTypeService) {
        this.employeeShiftTypeService = employeeShiftTypeService;
    }

    @GetMapping
    ResponseEntity<List<EmployeeShiftTypeResponse>> listActiveShiftTypes() {
        return ResponseEntity.ok(employeeShiftTypeService.listActiveShiftTypes());
    }
}
