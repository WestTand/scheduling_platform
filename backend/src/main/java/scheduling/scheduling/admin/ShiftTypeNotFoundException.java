package scheduling.scheduling.admin;

public class ShiftTypeNotFoundException extends RuntimeException {

    public ShiftTypeNotFoundException() {
        super("Shift type not found");
    }
}
