package brooklyn.util.javalang;

import org.testng.Assert;
import org.testng.annotations.Test;

public class StackTraceSimplifierTest {

    @Test
    public void isStackTraceElementUsefulRejectsABlacklistedPackage() {
        StackTraceElement el = new StackTraceElement("groovy.lang.Foo", "bar", "groovy/lang/Foo.groovy", 42);
        Assert.assertFalse(StackTraceSimplifier.isStackTraceElementUseful(el));
    }

    @Test
    public void isStackTraceElementUsefulAcceptsANonBlacklistedPackage() {
        StackTraceElement el = new StackTraceElement(
            "brooklyn.util.task", "StackTraceSimplifierTest", "StackTraceSimplifierTest.groovy", 42);
        Assert.assertTrue(StackTraceSimplifier.isStackTraceElementUseful(el));
    }
    
    @Test
    public void cleanTestTrace() {
        RuntimeException t = StackTraceSimplifier.newInstance(StackTraceSimplifierTest.class.getName())
            .cleaned(new RuntimeException("sample"));
        // should exclude *this* class also
        Assert.assertTrue(t.getStackTrace()[0].getClassName().startsWith("org.testng"),
                "trace was: "+t.getStackTrace()[0]);
    }
    
}
