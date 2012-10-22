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

    public RuntimeInterruptedException(InterruptedException cause) {
        super(cause);
        Thread.currentThread().interrupt();
    }

    @Override
    public InterruptedException getCause() {
        return (InterruptedException) super.getCause();
    }
}
