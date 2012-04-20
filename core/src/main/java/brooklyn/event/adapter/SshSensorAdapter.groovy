package brooklyn.event.adapter

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.event.Sensor
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.flags.FlagUtils
import brooklyn.util.internal.StringEscapeUtils


/**
 * TODO javadoc
 */
public class SshSensorAdapter extends AbstractSensorAdapter {

	public static final Logger log = LoggerFactory.getLogger(SshSensorAdapter.class)

	protected final SshPollHelper poller = new SshPollHelper(this);
	protected final Map env=[:]
	protected final SshMachineLocation location;

	protected String command

	public SshSensorAdapter(Map flags=[:], SshMachineLocation location, env=[:]) {
		super(flags);
		this.location = location;
        this.env << env
	}

	protected boolean isConnected() { isActivated() && poller!=null && poller.getLastWasSuccessful() }
	
	/** returns a new adapter, registered, with the given acommand and additional environment */ 
	public SshSensorAdapter command(String command, Map cmdEnv=[:]) {
		def newFlags = FlagUtils.getFieldsWithValues(this)
		def newAdapter = new SshSensorAdapter(newFlags, location, env)
		newAdapter.command = command
        newAdapter.env << cmdEnv
		if (registry) return registry.register(newAdapter);
		return newAdapter;
	}

	/** closure will run in an {@link SshResultContext}, default value is {@code stdout} */
	public void poll(Sensor s, Closure c={it}) {
		poller.addSensor(s, c);
	}

}
