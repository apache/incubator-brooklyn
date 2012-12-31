package brooklyn.event.adapter;

import groovy.json.JsonSlurper

import javax.annotation.Nullable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions;

import brooklyn.location.basic.SshMachineLocation


/**
 * Context object for evaluating sensor closures with ssh command results.
 * 
 * @deprecated See brooklyn.event.feed.ssh.SshFeed
 */
@Deprecated
public class SshResultContext extends AbstractSensorEvaluationContext {
	
	public static final Logger log = LoggerFactory.getLogger(SshResultContext.class);
	
	/** The machine the command will run on. */
	final SshMachineLocation location
	
	/** Command exit status, or -1 if error is set. */
	final int exitStatus;

	/** Command standard output; may be null if no content available. */
	@Nullable
	final String stdout

	/** Command standard error; may be null if no content available. */
	@Nullable
	final String stderr

	/** Usual constructor. */	
	public SshResultContext(SshMachineLocation location, int exitStatus, String stdout, String stderr) {
        this.location = Preconditions.checkNotNull(location, "location")
		this.exitStatus = exitStatus
        this.stdout = stdout
        this.stderr = stderr
	}

	/** Constructor for when there is an error. */
	public SshResultContext(SshMachineLocation location, Exception error) {
        this.location = Preconditions.checkNotNull(location, "location")
		this.exitStatus = -1
        this.stdout = null
        this.stderr = null
        this.error = Preconditions.checkNotNull(error, "error")
	}

	protected Object getDefaultValue() { return stdout }
    
}
