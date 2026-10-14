package scheduling.scheduling.employee;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.employee.dto.EmployeeShiftTypeResponse;
import scheduling.scheduling.repository.ShiftTypeRepository;

@Service
public class EmployeeShiftTypeService {

    private static final String ACTIVE = "ACTIVE";

    private final ShiftTypeRepository shiftTypeRepository;

    public EmployeeShiftTypeService(ShiftTypeRepository shiftTypeRepository) {
        this.shiftTypeRepository = shiftTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<EmployeeShiftTypeResponse> listActiveShiftTypes() {
        return shiftTypeRepository.findByStatusOrderByShiftNameAsc(ACTIVE).stream()
                .map(EmployeeShiftTypeResponse::from)
                .toList();
    }
}
