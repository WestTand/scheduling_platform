package scheduling.scheduling.auth;

public class InvalidActivationOtpException extends RuntimeException {

    public InvalidActivationOtpException() {
        super("Invalid activation OTP");
    }
}
