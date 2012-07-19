package brooklyn.cli;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;

import java.util.Map;

/**
 * Simple brooklyn app used for testing
 */
public class ExampleApp extends AbstractApplication {

    public ExampleApp(Map flags, Entity owner) {
        super(flags);
    }
}
