package brooklyn.util.task.ssh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.TaskFactory;
import brooklyn.util.config.ConfigBag;

// cannot be (cleanly) instantiated due to nested generic self-referential type; however trivial subclasses do allow it 
public class SshFetchTaskFactory implements TaskFactory<SshFetchTaskWrapper> {
    
    private static final Logger log = LoggerFactory.getLogger(SshFetchTaskFactory.class);
    
    boolean dirty = false;
    
    SshMachineLocation machine;
    String remoteFile;
    final ConfigBag config = ConfigBag.newInstance();

    /** constructor where machine will be added later */
    public SshFetchTaskFactory(String remoteFile) {
        remoteFile(remoteFile);
    }

    /** convenience constructor to supply machine immediately */
    public SshFetchTaskFactory(SshMachineLocation machine, String remoteFile) {
        machine(machine);
        remoteFile(remoteFile);
    }

    protected SshFetchTaskFactory self() { return this; }

    protected void markDirty() {
        dirty = true;
    }
    
    public SshFetchTaskFactory machine(SshMachineLocation machine) {
        markDirty();
        this.machine = machine;
        return self();
    }
        
    public SshMachineLocation getMachine() {
        return machine;
    }
    
    public SshFetchTaskFactory remoteFile(String remoteFile) {
        this.remoteFile = remoteFile;
        return self();
    }

    public ConfigBag getConfig() {
        return config;
    }
    
    @Override
    public SshFetchTaskWrapper newTask() {
        dirty = false;
        return new SshFetchTaskWrapper(this);
    }

    @Override
    protected void finalize() throws Throwable {
        // help let people know of API usage error
        if (dirty)
            log.warn("Task "+this+" was modified but modification was never used");
        super.finalize();
    }

}