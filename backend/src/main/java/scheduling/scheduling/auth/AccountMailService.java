package scheduling.scheduling.auth;

import scheduling.scheduling.entity.Account;

public interface AccountMailService {

    void sendActivationOtp(Account account, String rawOtp);
}
