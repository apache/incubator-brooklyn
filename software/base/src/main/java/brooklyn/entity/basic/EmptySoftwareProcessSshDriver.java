package brooklyn.entity.basic;

import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.location.basic.SshMachineLocation;


public class EmptySoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements EmptySoftwareProcessDriver {

    private final AtomicBoolean running = new AtomicBoolean();

    public EmptySoftwareProcessSshDriver(EmptySoftwareProcessImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void install() {
    }

    @Override
    public void customize() {
    }

    @Override
    public void launch() {
        running.set(true);
    }

    @Override
    public void rebind() {
        super.rebind();
        /* TODO not necessarily, but there is not yet an easy way to persist state without 
         * using config/sensors which we might not want do. */
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
    }
}
