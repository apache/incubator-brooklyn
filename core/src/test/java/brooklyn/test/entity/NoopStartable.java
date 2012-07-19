package brooklyn.test.entity;

import java.util.Collection;

import brooklyn.entity.trait.Startable;

public class NoopStartable implements Startable {
   public void start(Collection loc) {}
   public void stop() {}
   public void restart() {}
}
