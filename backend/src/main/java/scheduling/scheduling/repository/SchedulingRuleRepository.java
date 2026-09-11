package scheduling.scheduling.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.SchedulingRule;

public interface SchedulingRuleRepository extends JpaRepository<SchedulingRule, Long> {

    Optional<SchedulingRule> findByRuleCode(String ruleCode);

    List<SchedulingRule> findByRuleCodeIn(Collection<String> ruleCodes);
}
