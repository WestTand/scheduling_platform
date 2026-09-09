package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.AccountActivationOtp;

public interface AccountActivationOtpRepository extends JpaRepository<AccountActivationOtp, Long> {

    Optional<AccountActivationOtp> findTopByAccountIdAndUsedAtIsNullOrderByCreatedAtDesc(Long accountId);
}
