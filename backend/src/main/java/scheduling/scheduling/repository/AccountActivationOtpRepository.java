package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.AccountActivationOtp;

public interface AccountActivationOtpRepository extends JpaRepository<AccountActivationOtp, Long> {
}
