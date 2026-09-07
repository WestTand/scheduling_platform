package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);
}
