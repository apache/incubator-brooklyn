package brooklyn.util.internal.ssh;

public class SshException extends RuntimeException {

    private static final long serialVersionUID = -5690230838066860965L;

    public SshException(String msg) {
        super(msg);
    }
    
    public SshException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
