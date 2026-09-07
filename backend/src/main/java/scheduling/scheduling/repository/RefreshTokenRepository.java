package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
