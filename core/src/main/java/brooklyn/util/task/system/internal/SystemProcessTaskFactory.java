package brooklyn.util.task.system.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.process.ProcessTool;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.base.Function;

public class SystemProcessTaskFactory<T extends SystemProcessTaskFactory<T,RET>,RET> extends AbstractProcessTaskFactory<T, RET> {

    private static final Logger log = LoggerFactory.getLogger(SystemProcessTaskFactory.class);
    
    public SystemProcessTaskFactory(String ...commands) {
        super(commands);
    }
    
    @Override
    public T machine(SshMachineLocation machine) {
        log.warn("Not permitted to set machines on "+this+" (ignoring - "+machine+")");
        if (log.isDebugEnabled())
            log.debug("Source of attempt to set machines on "+this+" ("+machine+")",
                    new Throwable("Source of attempt to set machines on "+this+" ("+machine+")"));
        return self();
    }

    @Override
    public ProcessTaskWrapper<RET> newTask() {
        return new SystemProcessTaskWrapper();
    }

    protected class SystemProcessTaskWrapper extends ProcessTaskWrapper<RET> {
        protected final String taskTypeShortName;
        
        public SystemProcessTaskWrapper() {
            this("Process");
        }
        public SystemProcessTaskWrapper(String taskTypeShortName) {
            super(SystemProcessTaskFactory.this);
            this.taskTypeShortName = taskTypeShortName;
        }
        @Override
        protected void run(ConfigBag config) {
            if (this.runAsScript==Boolean.FALSE)
                this.exitCode = newExecWithLoggingHelpers().execCommands(config.getAllConfigRaw(), getSummary(), getCommands(), getShellEnvironment());
            else // runScript = null or TRUE
                this.exitCode = newExecWithLoggingHelpers().execScript(config.getAllConfigRaw(), getSummary(), getCommands(), getShellEnvironment());
        }
        protected String taskTypeShortName() { return taskTypeShortName; }
    }
    
    protected ExecWithLoggingHelpers newExecWithLoggingHelpers() {
        return new ExecWithLoggingHelpers("Process") {
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

    /** concrete instance (for generics) */
    public static class ConcreteSystemProcessTaskFactory<RET> extends SystemProcessTaskFactory<ConcreteSystemProcessTaskFactory<RET>, RET> {
        public ConcreteSystemProcessTaskFactory(String ...commands) {
            super(commands);
        }
    }
    
}
