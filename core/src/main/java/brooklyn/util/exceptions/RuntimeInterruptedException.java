package brooklyn.util.exceptions;

/**
 * A {@link RuntimeException} that is thrown when a Thread is interrupted.
 *
 * This exception is useful if a Thread needs to be interrupted, but the {@link InterruptedException} can't be thrown
 * because it is checked.
 *
 * When the RuntimeInterruptedException is created, it will automatically set the interrupt status on the calling
 * thread.
 *
 * @author Peter Veentjer.
 */
public class RuntimeInterruptedException extends RuntimeException {

    private static final long serialVersionUID = 915050245927866175L;

    public RuntimeInterruptedException(InterruptedException cause) {
        super(cause);
        Thread.currentThread().interrupt();
    }

    public RuntimeInterruptedException(String msg, InterruptedException cause) {
        super(msg, cause);
        Thread.currentThread().interrupt();
    }

    @Override
    public InterruptedException getCause() {
        return (InterruptedException) super.getCause();
    }
}
