package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.OptimizationRun;

public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
}
