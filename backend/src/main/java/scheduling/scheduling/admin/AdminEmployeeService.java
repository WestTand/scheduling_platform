package scheduling.scheduling.admin;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import scheduling.scheduling.admin.dto.AdminEmployeeResponse;
import scheduling.scheduling.auth.RefreshTokenService;
import scheduling.scheduling.repository.EmployeeRepository;

@Service
public class AdminEmployeeService {

    private final EmployeeRepository employeeRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminEmployeeService(EmployeeRepository employeeRepository, RefreshTokenService refreshTokenService) {
        this.employeeRepository = employeeRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminEmployeeResponse> listEmployees(String query, String status) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        if (normalizedQuery == null && normalizedStatus == null) {
            return employeeRepository.findAll().stream()
                    .map(AdminEmployeeResponse::from)
                    .toList();
        }
        return employeeRepository.search(normalizedQuery, normalizedStatus).stream()
                .map(AdminEmployeeResponse::from)
                .toList();
    }
}
