package brooklyn.event.adapter;

import groovy.json.JsonSlurper

import javax.annotation.Nullable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.basic.SshMachineLocation


/**
 * Context object for evaluating sensor closures with ssh command results.
 */
public class SshResultContext extends AbstractSensorEvaluationContext {
	
	public static final Logger log = LoggerFactory.getLogger(SshResultContext.class);
	
	/** may be null during testing */
	@Nullable
	final SshMachineLocation location
	
	/** command exit status, or -1 if error is set */
	final int exitStatus;
	/** command standard output; may be null if no content available */
	@Nullable
	final String stdout
	/** command standard error; may be null if no content available */
	@Nullable
	final String stderr

	/** usual constructor */	
	public SshResultContext(SshMachineLocation location, int exitStatus, String stdout, String stderr) {
        this.location = location
		this.exitStatus = exitStatus
        this.stdout = stdout
        this.stderr = stderr
	}

	/** constructor for when there is an error; note that initial ssh connection may throw errors */
	public SshResultContext(SshMachineLocation location, Exception error) {
        this.location = location
		this.exitStatus = -1
        this.stdout = null
        this.stderr = null
        this.error = error
	}

	protected Object getDefaultValue() { return stdout }
    
    public String getStdout() { return stdout }
    
    public String getStderr() { return stderr }
	
	public int getExitStatus() { return exitStatus }
}
