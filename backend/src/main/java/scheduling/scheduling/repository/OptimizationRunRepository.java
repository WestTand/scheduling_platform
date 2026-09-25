package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import scheduling.scheduling.entity.OptimizationRun;

public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
    Optional<OptimizationRun> findTopBySchedulePeriodIdOrderByStartedAtDescIdDesc(Long schedulePeriodId);
}
