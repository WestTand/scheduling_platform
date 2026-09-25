package scheduling.scheduling.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import scheduling.scheduling.entity.OptimizationRun;

public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
    Optional<OptimizationRun> findTopBySchedulePeriodIdOrderByStartedAtDescIdDesc(Long schedulePeriodId);

    List<OptimizationRun> findAllBySchedulePeriodId(Long schedulePeriodId);
}
