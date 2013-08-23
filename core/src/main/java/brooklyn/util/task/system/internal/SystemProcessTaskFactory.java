package brooklyn.util.task.system.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.base.Function;

public class SystemProcessTaskFactory<T> extends AbstractProcessTaskFactory<SystemProcessTaskFactory<T>, T> {

    private static final Logger log = LoggerFactory.getLogger(SystemProcessTaskFactory.class);
    
    public SystemProcessTaskFactory(String ...commands) {
        super(commands);
    }
    
    @Override
    public ProcessTaskFactory<T> machine(SshMachineLocation machine) {
        log.warn("Not permitted to set machines on "+this+" ("+machine+")");
        if (log.isDebugEnabled())
            log.debug("Source of attempt to set machines on "+this+" ("+machine+")",
                    new Throwable("Source of attempt to set machines on "+this+" ("+machine+")"));
        return this;
    }

    @Override
    public ProcessTaskWrapper<T> newTask() {
        return new ProcessTaskWrapper<T>(this) {
            @Override
            protected void run(ConfigBag config) {
                if (this.runAsScript==Boolean.FALSE)
                    this.exitCode = newExecWithLoggingHelpers().execCommands(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                else // runScript = null or TRUE
                    this.exitCode = newExecWithLoggingHelpers().execScript(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
            }
        };
    }

    protected ExecWithLoggingHelpers newExecWithLoggingHelpers() {
        return new ExecWithLoggingHelpers() {
            @Override
            protected <U> U execWithTool(MutableMap<String, Object> props, Function<ShellTool, U> task) {
                // properties typically passed to both
                if (log.isDebugEnabled() && props!=null && !props.isEmpty())
                    log.debug("Ignoring flags "+props+" when running "+this);
                return task.apply(new ProcessTool());
            }
            @Override
            protected void preExecChecks() {}
            @Override
            protected String constructDefaultLoggingPrefix(ConfigBag execFlags) {
                return "system.exec";
            }
        }.logger(log);
    }

}
