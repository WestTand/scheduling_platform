package scheduling.scheduling.auth;

public class InvalidPasswordChangeStateException extends RuntimeException {

    public InvalidPasswordChangeStateException() {
        super("Invalid password change state");
    }
}
