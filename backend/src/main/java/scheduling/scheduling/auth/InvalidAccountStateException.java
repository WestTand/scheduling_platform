package scheduling.scheduling.auth;

public class InvalidAccountStateException extends RuntimeException {

    public InvalidAccountStateException() {
        super("Invalid account state");
    }
}
