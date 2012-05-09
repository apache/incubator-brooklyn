package brooklyn.event.adapter

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions

import brooklyn.event.Sensor
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag


/**
 * TODO javadoc
 */
public class SshSensorAdapter extends AbstractSensorAdapter {

	public static final Logger log = LoggerFactory.getLogger(SshSensorAdapter.class)

	@SetFromFlag
	Map env
	@SetFromFlag
	String command

	protected final SshPollHelper poller = new SshPollHelper(this)
	protected final SshMachineLocation location;

	public SshSensorAdapter(Map flags=[:], SshMachineLocation location) {
		super(flags)
		this.location = Preconditions.checkNotNull(location, "location")
        if (!env) env = [:]
    }

	protected boolean isConnected() { isActivated() && poller != null && poller.getLastWasSuccessful() }

    /** returns a new adapter, registered, with the given command and optional additional environment */
    public SshSensorAdapter command(String command, Map cmdEnv=[:]) {
        def newFlags = FlagUtils.getFieldsWithValues(this)
        def newAdapter = new SshSensorAdapter(newFlags, location)
        newAdapter.command = Preconditions.checkNotNull(command, "command")
        newAdapter.env << cmdEnv
        if (registry) return registry.register(newAdapter);
        return newAdapter;
    }

    /** returns a new adapter, registered, with the same command and additional environment */
    public SshSensorAdapter env(Map cmdEnv) {
        def newFlags = FlagUtils.getFieldsWithValues(this)
        def newAdapter = new SshSensorAdapter(newFlags, location)
        newAdapter.env << cmdEnv
        if (registry) return registry.register(newAdapter)
        return newAdapter;
    }

	/** closure will run in an {@link SshResultContext}, default value is {@code stdout} */
	public void poll(Sensor s, Closure c={it}) {
        Preconditions.checkNotNull(command, "command")
		poller.addSensor(s, c);
	}

}
