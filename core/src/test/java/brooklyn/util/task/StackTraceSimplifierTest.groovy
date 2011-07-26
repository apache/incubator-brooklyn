package brooklyn.util.task

import static org.testng.AssertJUnit.*
import org.testng.annotations.Test

class StackTraceSimplifierTest {

    @Test
    public void isStackTraceElementUsefulRejectsABlacklistedPackage() {
        StackTraceElement el = new StackTraceElement("groovy.lang.Foo", "bar", "groovy/lang/Foo.groovy", 42)
        assertFalse StackTraceSimplifier.isStackTraceElementUseful(el)
    }

    @Test
    public void isStackTraceElementUsefulAcceptsANonBlacklistedPackage() {
        StackTraceElement el = new StackTraceElement(
            "brooklyn.util.task", "StackTraceSimplifierTest", "StackTraceSimplifierTest.groovy", 42)
        assertTrue StackTraceSimplifier.isStackTraceElementUseful(el)
    }
}
