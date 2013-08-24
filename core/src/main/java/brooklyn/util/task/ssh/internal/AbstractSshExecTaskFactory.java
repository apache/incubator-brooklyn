package brooklyn.util.task.ssh.internal;

import com.google.common.base.Preconditions;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.internal.AbstractProcessTaskFactory;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public class AbstractSshExecTaskFactory<T extends AbstractProcessTaskFactory<T,RET>,RET> extends AbstractProcessTaskFactory<T,RET> implements ProcessTaskFactory<RET> {
    
    /** constructor where machine will be added later */
    public AbstractSshExecTaskFactory(String ...commands) {
        super(commands);
    }

    /** convenience constructor to supply machine immediately */
    public AbstractSshExecTaskFactory(SshMachineLocation machine, String ...commands) {
        this(commands);
        machine(machine);
    }

    public T machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }
    
    @Override
    public ProcessTaskWrapper<RET> newTask() {
        dirty = false;
        return new ProcessTaskWrapper<RET>(this) {
            protected void run(ConfigBag config) {
                Preconditions.checkNotNull(getMachine(), "machine");
                if (this.runAsScript==Boolean.FALSE)
                    this.exitCode = getMachine().execCommands(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
                else // runScript = null or TRUE
                    this.exitCode = getMachine().execScript(config.getAllConfigRaw(), getSummary(), commands, shellEnvironment);
            }
        };
    }
}