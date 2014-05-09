package brooklyn.test;

import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.util.concurrent.Callables;

public class AssertsTest {

    // TODO this is confusing -- i'd expect it to fail since it always returns false;
    // see notes at start of Asserts and in succeedsEventually method
    @Test
    public void testSucceedsEventually() {
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.millis(50)), Callables.returning(false));
    }
    
}
