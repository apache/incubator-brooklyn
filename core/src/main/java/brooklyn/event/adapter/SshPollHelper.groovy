package brooklyn.event.adapter;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Charsets

/**
 * Captures output and exit code for {@link SshSensorAdapter}.
 *
 * @see SshSensorAdapter
 * 
 * @deprecated See brooklyn.event.feed.ssh.SshFeed
 */
@Deprecated
protected class SshPollHelper extends AbstractPollHelper {

    public static final Logger log = LoggerFactory.getLogger(SshPollHelper.class);

    public SshPollHelper(SshSensorAdapter adapter) {
        super(adapter);
    }

    SshSensorAdapter getAdapter() {
        return (SshSensorAdapter) super.getAdapter();
    }
    
    @Override
    protected String getOptionalContextForErrors(AbstractSensorEvaluationContext response) {
        response?.stdout
    }

    @Override
    AbstractSensorEvaluationContext executePollOnSuccess() {
        if (log.isDebugEnabled()) log.debug "ssh polling for {} sensors using {}", adapter.entity, adapter.command
        ByteArrayOutputStream stdout = []
        ByteArrayOutputStream stderr = []

        def exitStatus = adapter.location.run(out:stdout, err:stderr, adapter.command, adapter.env);
        def result = new SshResultContext(adapter.location, exitStatus, stdout.toString(Charsets.UTF_8.name()), stderr.toString(Charsets.UTF_8.name()))
        if (log.isDebugEnabled()) log.debug "ssh poll for {} got: {}", adapter.entity, result.stdout
        return result
    }

    @Override
    AbstractSensorEvaluationContext executePollOnError(Exception error) {
        return new SshResultContext(adapter.location, error)
    }
}
