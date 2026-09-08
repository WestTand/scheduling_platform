# Auth JWT Refresh Cookie Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build employee registration, login, JWT access authentication, refresh token rotation in an HTTP-only cookie, logout revocation, and current-user lookup.

**Architecture:** The backend will use stateless JWT access tokens for API authorization and opaque refresh tokens stored as hashes in PostgreSQL. Auth code is split into focused packages: `auth` for API/service/security/JWT and `repository` for persistence.

**Tech Stack:** Spring Boot 4.1.1, Spring Security, Spring Data JPA, PostgreSQL, BCrypt, JJWT, JUnit 5, AssertJ, MockMvc.

---

## File Map

- Modify: `backend/pom.xml` to add JJWT and validation dependencies.
- Modify: `backend/src/main/resources/application.properties` to add JWT/cookie configuration.
- Modify: `backend/src/main/java/scheduling/scheduling/HealthController.java` to remove the temporary `HealthSecurityConfig`.
- Create: `backend/src/main/java/scheduling/scheduling/entity/RefreshToken.java` for persisted refresh token metadata.
- Create: `backend/src/main/java/scheduling/scheduling/repository/AccountRepository.java`.
- Create: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`.
- Create: `backend/src/main/java/scheduling/scheduling/repository/RefreshTokenRepository.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/JwtService.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/RefreshTokenService.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/JwtAuthenticationFilter.java`.
- Create: `backend/src/main/java/scheduling/scheduling/auth/CurrentAccountPrincipal.java`.
- Create DTOs under `backend/src/main/java/scheduling/scheduling/auth/dto/`.
- Create exceptions under `backend/src/main/java/scheduling/scheduling/auth/`.
- Create tests under `backend/src/test/java/scheduling/scheduling/auth/` and extend `EntityMappingTest`.

---

### Task 1: Dependencies And Configuration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: Add JWT and validation dependencies**

Update `backend/pom.xml` dependencies with:

```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-api</artifactId>
	<version>0.12.6</version>
</dependency>
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-impl</artifactId>
	<version>0.12.6</version>
	<scope>runtime</scope>
</dependency>
<dependency>
	<groupId>io.jsonwebtoken</groupId>
	<artifactId>jjwt-jackson</artifactId>
	<version>0.12.6</version>
	<scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: Add auth application properties**

Append to `backend/src/main/resources/application.properties`:

```properties
app.jwt.secret=${JWT_SECRET:dev-only-change-me-dev-only-change-me-dev-only-change-me}
app.jwt.access-token-ttl-seconds=${JWT_ACCESS_TOKEN_TTL_SECONDS:900}
app.auth.refresh-token-ttl-seconds=${REFRESH_TOKEN_TTL_SECONDS:604800}
app.auth.refresh-cookie-name=${REFRESH_COOKIE_NAME:refresh_token}
app.auth.refresh-cookie-secure=${REFRESH_COOKIE_SECURE:false}
```

- [ ] **Step 3: Verify compile**

Run: `./mvnw.cmd -DskipTests compile`

Expected: build success.

- [ ] **Step 4: Commit**

```powershell
git add backend/pom.xml backend/src/main/resources/application.properties
git commit -m "chore: add auth dependencies and config"
```

---

### Task 2: Refresh Token Entity And Repositories

**Files:**
- Modify test: `backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/entity/RefreshToken.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/AccountRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/EmployeeRepository.java`
- Create: `backend/src/main/java/scheduling/scheduling/repository/RefreshTokenRepository.java`

- [ ] **Step 1: Extend the failing entity mapping test**

In `EntityMappingTest.entitiesUseDocumentedPrimaryKeyColumnNames`, add:

```java
Map.entry(RefreshToken.class, "refresh_token_id"),
```

Add a test for refresh token columns:

```java
@Test
void refreshTokenDeclaresRequiredColumns() {
    assertThat(declaredColumnNames(RefreshToken.class))
            .contains("token_hash", "expires_at", "revoked_at", "created_at")
            .doesNotContain("updated_at");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=EntityMappingTest test`

Expected: compile fails because `RefreshToken` does not exist, or test fails because mapping is missing.

- [ ] **Step 3: Create `RefreshToken` entity**

Create `backend/src/main/java/scheduling/scheduling/entity/RefreshToken.java`:

```java
package scheduling.scheduling.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@AttributeOverride(name = "id", column = @Column(name = "refresh_token_id"))
@Table(name = "refresh_tokens", uniqueConstraints = {@UniqueConstraint(columnNames = {"token_hash"})})
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
```

- [ ] **Step 4: Create repositories**

Create `AccountRepository.java`:

```java
package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

Create `EmployeeRepository.java`:

```java
package scheduling.scheduling.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    boolean existsByEmployeeCode(String employeeCode);
}
```

Create `RefreshTokenRepository.java`:

```java
package scheduling.scheduling.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import scheduling.scheduling.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
```

- [ ] **Step 5: Run mapping test**

Run: `./mvnw.cmd -Dtest=EntityMappingTest test`

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/entity/RefreshToken.java backend/src/main/java/scheduling/scheduling/repository backend/src/test/java/scheduling/scheduling/entity/EntityMappingTest.java
git commit -m "feat: add refresh token persistence"
```

---

### Task 3: JWT Service

**Files:**
- Create test: `backend/src/test/java/scheduling/scheduling/auth/JwtServiceTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/JwtService.java`

- [ ] **Step 1: Write failing JWT tests**

Create `JwtServiceTest.java`:

```java
package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.entity.AccountRole;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-test-secret-test-secret-test-secret",
            Duration.ofMinutes(15));

    @Test
    void createsAndParsesAccessToken() {
        String token = jwtService.createAccessToken(10L, "employee01", AccountRole.EMPLOYEE);

        CurrentAccountPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.accountId()).isEqualTo(10L);
        assertThat(principal.username()).isEqualTo("employee01");
        assertThat(principal.role()).isEqualTo(AccountRole.EMPLOYEE);
    }

    @Test
    void rejectsInvalidAccessToken() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("bad-token"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=JwtServiceTest test`

Expected: compile fails because `JwtService`, `CurrentAccountPrincipal`, and `InvalidTokenException` do not exist.

- [ ] **Step 3: Create principal and exception**

Create `CurrentAccountPrincipal.java`:

```java
package scheduling.scheduling.auth;

import scheduling.scheduling.entity.AccountRole;

public record CurrentAccountPrincipal(Long accountId, String username, AccountRole role) {
}
```

Create `InvalidTokenException.java`:

```java
package scheduling.scheduling.auth;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create `JwtService`**

Create `JwtService.java`:

```java
package scheduling.scheduling.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import scheduling.scheduling.entity.AccountRole;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
        this(secret, Duration.ofSeconds(accessTokenTtlSeconds));
    }

    JwtService(String secret, Duration accessTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.clock = Clock.systemUTC();
    }

    public String createAccessToken(Long accountId, String username, AccountRole role) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(username)
                .claim("accountId", accountId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public CurrentAccountPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long accountId = claims.get("accountId", Long.class);
            String username = claims.getSubject();
            AccountRole role = AccountRole.valueOf(claims.get("role", String.class));
            return new CurrentAccountPrincipal(accountId, username, role);
        } catch (IllegalArgumentException | JwtException exception) {
            throw new InvalidTokenException("Invalid access token");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
```

- [ ] **Step 5: Run JWT tests**

Run: `./mvnw.cmd -Dtest=JwtServiceTest test`

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/auth backend/src/test/java/scheduling/scheduling/auth/JwtServiceTest.java
git commit -m "feat: add jwt access token service"
```

---

### Task 4: Refresh Token Service

**Files:**
- Create test: `backend/src/test/java/scheduling/scheduling/auth/RefreshTokenServiceTest.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/RefreshTokenService.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/RefreshTokenResult.java`

- [ ] **Step 1: Write failing refresh service tests**

Create tests using Mockito-backed repositories:

```java
package scheduling.scheduling.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.RefreshToken;
import scheduling.scheduling.repository.RefreshTokenRepository;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC);
    private final RefreshTokenService service = new RefreshTokenService(repository, Duration.ofDays(7), clock);

    @Test
    void createsOpaqueRefreshTokenAndStoresOnlyHash() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Account account = Account.builder().username("employee01").build();

        RefreshTokenResult result = service.create(account);

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.entity().getTokenHash()).isNotEqualTo(result.rawToken());
        assertThat(result.entity().getAccount()).isSameAs(account);
        assertThat(result.entity().getExpiresAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(7)), ZoneOffset.UTC));
    }

    @Test
    void rejectsRevokedToken() {
        RefreshToken revoked = RefreshToken.builder()
                .tokenHash(service.hashTokenForTest("raw"))
                .expiresAt(LocalDateTime.of(2026, 9, 8, 0, 0))
                .revokedAt(LocalDateTime.of(2026, 9, 7, 1, 0))
                .build();
        when(repository.findByTokenHash(service.hashTokenForTest("raw"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.validate("raw"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .tokenHash(service.hashTokenForTest("raw"))
                .expiresAt(LocalDateTime.of(2026, 9, 6, 0, 0))
                .build();
        when(repository.findByTokenHash(service.hashTokenForTest("raw"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validate("raw"))
                .isInstanceOf(InvalidTokenException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=RefreshTokenServiceTest test`

Expected: compile fails because refresh service classes do not exist.

- [ ] **Step 3: Implement `RefreshTokenResult`**

```java
package scheduling.scheduling.auth;

import scheduling.scheduling.entity.RefreshToken;

public record RefreshTokenResult(String rawToken, RefreshToken entity) {
}
```

- [ ] **Step 4: Implement `RefreshTokenService`**

```java
package scheduling.scheduling.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.RefreshToken;
import scheduling.scheduling.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenTtl;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.auth.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this(refreshTokenRepository, Duration.ofSeconds(refreshTokenTtlSeconds), Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository, Duration refreshTokenTtl, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    @Transactional
    public RefreshTokenResult create(Account account) {
        String rawToken = generateRawToken();
        RefreshToken entity = RefreshToken.builder()
                .account(account)
                .tokenHash(hash(rawToken))
                .expiresAt(now().plus(refreshTokenTtl))
                .build();
        return new RefreshTokenResult(rawToken, refreshTokenRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (token.isRevoked() || token.isExpired(now())) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        return token;
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(now()));
    }

    @Transactional
    public RefreshTokenResult rotate(String rawToken) {
        RefreshToken oldToken = validate(rawToken);
        oldToken.setRevokedAt(now());
        return create(oldToken.getAccount());
    }

    String hashTokenForTest(String rawToken) {
        return hash(rawToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
```

- [ ] **Step 5: Run refresh token tests**

Run: `./mvnw.cmd -Dtest=RefreshTokenServiceTest test`

Expected: tests pass.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/auth/RefreshTokenResult.java backend/src/main/java/scheduling/scheduling/auth/RefreshTokenService.java backend/src/test/java/scheduling/scheduling/auth/RefreshTokenServiceTest.java
git commit -m "feat: add refresh token service"
```

---

### Task 5: Auth Service Registration Login Refresh Logout

**Files:**
- Create test: `backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java`
- Create/modify: `backend/src/main/java/scheduling/scheduling/auth/AuthService.java`
- Create DTOs under `backend/src/main/java/scheduling/scheduling/auth/dto/`
- Create exceptions: `DuplicateResourceException.java`, `AuthenticationFailedException.java`

- [ ] **Step 1: Write failing service tests**

Cover these cases in `AuthServiceTest`:

```java
@Test
void registerCreatesEmployeeAndEmployeeAccount() { }

@Test
void registerRejectsDuplicateUsername() { }

@Test
void registerRejectsDuplicateEmployeeCode() { }

@Test
void loginRejectsInvalidCredentials() { }

@Test
void loginRejectsInactiveAccount() { }

@Test
void refreshRotatesRefreshTokenAndReturnsNewAccessToken() { }

@Test
void logoutRevokesRefreshTokenWhenCookieExists() { }
```

Use mocked repositories/services, `BCryptPasswordEncoder`, and AssertJ assertions. Expected initial result: compile failure because `AuthService` and DTOs do not exist.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=AuthServiceTest test`

Expected: compile fails due to missing auth service/DTOs.

- [ ] **Step 3: Create request DTOs**

Create `RegisterRequest.java`, `LoginRequest.java` as records with validation annotations:

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String employeeCode,
        @NotBlank String fullName,
        String email,
        String phone) {
}
```

```java
package scheduling.scheduling.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
```

- [ ] **Step 4: Create response DTOs**

Create `AccountResponse.java`, `EmployeeResponse.java`, `AuthResponse.java`:

```java
package scheduling.scheduling.auth.dto;

import scheduling.scheduling.entity.Account;
import scheduling.scheduling.entity.AccountRole;

public record AccountResponse(Long id, String username, AccountRole role) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getRole());
    }
}
```

```java
package scheduling.scheduling.auth.dto;

import scheduling.scheduling.entity.Employee;

public record EmployeeResponse(Long id, String employeeCode, String fullName, String email, String phone, String status) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getEmployeeCode(), employee.getFullName(), employee.getEmail(), employee.getPhone(), employee.getStatus());
    }
}
```

```java
package scheduling.scheduling.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, AccountResponse account) {
}
```

Create `MeResponse.java`:

```java
package scheduling.scheduling.auth.dto;

public record MeResponse(AccountResponse account, EmployeeResponse employee) {
}
```

- [ ] **Step 5: Create exceptions**

```java
package scheduling.scheduling.auth;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
```

```java
package scheduling.scheduling.auth;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("Invalid username or password");
    }
}
```

- [ ] **Step 6: Implement `AuthService`**

Implement methods:

```java
public AccountResponse register(RegisterRequest request)
public LoginResult login(LoginRequest request)
public LoginResult refresh(String rawRefreshToken)
public void logout(String rawRefreshToken)
public MeResponse me(CurrentAccountPrincipal principal)
```

Also create:

```java
package scheduling.scheduling.auth;

import scheduling.scheduling.auth.dto.AuthResponse;

public record LoginResult(AuthResponse response, String refreshToken) {
}
```

Implementation rules:

- `register`: check duplicates, save `Employee`, save linked `Account` with `AccountRole.EMPLOYEE` and BCrypt hash.
- `login`: find account, require `isActive=true`, verify password, update `lastLogin`, create JWT, create refresh token.
- `refresh`: rotate refresh token and return new access token for token owner.
- `logout`: call `refreshTokenService.revoke(rawRefreshToken)` if token is not blank.
- `me`: load account by ID from repository, include employee when present.

- [ ] **Step 7: Run service tests**

Run: `./mvnw.cmd -Dtest=AuthServiceTest test`

Expected: tests pass.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/auth backend/src/main/java/scheduling/scheduling/auth/dto backend/src/test/java/scheduling/scheduling/auth/AuthServiceTest.java
git commit -m "feat: add auth service flows"
```

---

### Task 6: Security Filter And Auth Controller

**Files:**
- Modify: `backend/src/main/java/scheduling/scheduling/HealthController.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/SecurityConfig.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/scheduling/scheduling/auth/AuthController.java`
- Create test: `backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java`

- [ ] **Step 1: Write failing controller/security tests**

Create `AuthControllerTest` with `@SpringBootTest` + `@AutoConfigureMockMvc`, mock `AuthService` where practical, and assert:

```java
@Test
void registerIsPublicAndReturnsCreatedAccount() throws Exception { }

@Test
void loginSetsHttpOnlyRefreshCookie() throws Exception { }

@Test
void refreshReadsCookieAndSetsRotatedCookie() throws Exception { }

@Test
void logoutClearsRefreshCookie() throws Exception { }

@Test
void meRejectsMissingBearerToken() throws Exception { }
```

Expected initial result: compile failure because controller/security classes are missing.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd -Dtest=AuthControllerTest test`

Expected: compile fails or endpoint tests return 404.

- [ ] **Step 3: Remove temporary security config from health controller**

Keep only:

```java
@RestController
class HealthController {
    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("service", "backend", "status", "UP");
    }
}
```

- [ ] **Step 4: Implement JWT filter**

Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`. It must read `Authorization`, require `Bearer ` prefix, parse JWT with `JwtService`, and set a `UsernamePasswordAuthenticationToken` containing `CurrentAccountPrincipal` and `ROLE_<role>` authority.

- [ ] **Step 5: Implement security config**

Create `SecurityConfig` with:

```java
@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
@Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception { ... }
```

Rules:

- Disable CSRF for API-only backend.
- Set session policy stateless.
- Permit `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`.
- Authenticate all other requests.
- Add JWT filter before `UsernamePasswordAuthenticationFilter`.

- [ ] **Step 6: Implement auth controller**

`AuthController` endpoints:

```java
@PostMapping("/register") ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request)
@PostMapping("/login") ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response)
@PostMapping("/refresh") ResponseEntity<AuthResponse> refresh(@CookieValue(name = "${app.auth.refresh-cookie-name}") String refreshToken, HttpServletResponse response)
@PostMapping("/logout") ResponseEntity<Void> logout(@CookieValue(name = "${app.auth.refresh-cookie-name}", required = false) String refreshToken, HttpServletResponse response)
@GetMapping("/me") MeResponse me(@AuthenticationPrincipal CurrentAccountPrincipal principal)
```

Set refresh cookie from `LoginResult.refreshToken()` with `HttpOnly`, configured `Secure`, `SameSite=Lax`, and `Path=/api/auth`. Clear cookie by setting `Max-Age=0`.

- [ ] **Step 7: Add exception handling**

Create `AuthExceptionHandler` with `@RestControllerAdvice` mapping:

- `DuplicateResourceException` -> `409`.
- `AuthenticationFailedException`, `InvalidTokenException` -> `401`.
- `MethodArgumentNotValidException` -> `400`.

Response body:

```java
public record ErrorResponse(String code, String message) { }
```

- [ ] **Step 8: Run controller tests**

Run: `./mvnw.cmd -Dtest=AuthControllerTest test`

Expected: tests pass.

- [ ] **Step 9: Commit**

```powershell
git add backend/src/main/java/scheduling/scheduling/HealthController.java backend/src/main/java/scheduling/scheduling/auth backend/src/test/java/scheduling/scheduling/auth/AuthControllerTest.java
git commit -m "feat: expose jwt auth endpoints"
```

---

### Task 7: Final Verification

**Files:**
- All backend auth files.

- [ ] **Step 1: Run targeted auth tests**

Run: `./mvnw.cmd -Dtest=JwtServiceTest,RefreshTokenServiceTest,AuthServiceTest,AuthControllerTest,EntityMappingTest test`

Expected: all targeted tests pass.

- [ ] **Step 2: Run full backend tests**

Run: `./mvnw.cmd test`

Expected: full backend test suite passes.

- [ ] **Step 3: Check diff cleanliness**

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 4: Review git status and log**

Run: `git status --short --branch`

Expected: only intended files are modified or clean after commits.

Run: `git log --oneline -10`

Expected: auth commits are present on top of `main`.

- [ ] **Step 5: Push**

```powershell
git push origin main
```

Expected: push succeeds.
