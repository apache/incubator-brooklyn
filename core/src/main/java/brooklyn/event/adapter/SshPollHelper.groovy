package brooklyn.event.adapter;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * TODO javadoc
 */
protected class SshPollHelper extends AbstractPollHelper {

	public static final Logger log = LoggerFactory.getLogger(SshPollHelper.class);
	
	final SshSensorAdapter adapter
	
	public SshPollHelper(SshSensorAdapter adapter) {
		super(adapter);
		this.adapter = adapter;
	}
	
	@Override
	protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) {
        response?.stdout
    }
	
	@Override
	AbstractSensorEvaluationContext executePollOnSuccess() {
        if (log.isDebugEnabled()) log.debug "ssh polling for {} sensors using {}", adapter.entity, adapter.command
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        try {
		    def exitStatus = adapter.location.run(out:stdout, err:stderr, adapter.command, adapter.env);
	        def result = new SshResultContext(adapter.location, exitStatus, stdout.toString(), stderr.toString())
	        if (log.isDebugEnabled()) log.debug "ssh poll for {} got: {}", adapter.entity, result.stdout
			return result
        } finally {
            stdout.close()
            stderr.close()
        }
	}
	
	@Override
	AbstractSensorEvaluationContext executePollOnError(Exception error) {
		try {
			return new SshResultContext(adapter.location, error)
		} catch (Exception e) {
			return null
		}
	}
}
