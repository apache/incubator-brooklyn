package brooklyn.util.stream;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.stream.ThreadLocalPrintStream.OutputCapturingContext;

public class ThreadLocalStdoutStderrTest {

    /** simple example showing how a capture to stdout can be set up */
    @Test
    public void testStdoutCapture() {
        OutputCapturingContext capture = ThreadLocalPrintStream.stdout().captureTee();
        System.out.println("hello");
        String out = capture.end();
        Assert.assertEquals("hello", out.trim());
        
        System.out.println("goodbye - not captured, restored normal output");
        Assert.assertEquals("hello", out.trim());
    }

    @Test
    public void testStdoutCaptureDetail() {
        ThreadLocalPrintStream.stdout();
        System.out.println("1 - not captured, but next goes to capture only");
        OutputCapturingContext capture = ThreadLocalPrintStream.stdout().capture();
        final String TWO = "2 - captured";
        System.out.println(TWO);
        Assert.assertEquals(TWO, capture.getOutputSoFar().trim());
        String out = capture.end();
        Assert.assertEquals(TWO, out.trim());
        System.out.println("3 - not captured, restored normal output");
        Assert.assertEquals(TWO, capture.getOutputSoFar().trim());
    }
    
    @Test
    public void testStderrCaptureDetail() {
        ThreadLocalPrintStream.stderr();
        System.err.println("1 - not captured, but next goes to capture only");
        OutputCapturingContext capture = ThreadLocalPrintStream.stderr().capture();
        final String TWO = "2 - captured";
        System.err.println(TWO);
        Assert.assertEquals(TWO, capture.getOutputSoFar().trim());
        String out = capture.end();
        Assert.assertEquals(TWO, out.trim());
        System.err.println("3 - not captured, restored normal output");
        Assert.assertEquals(TWO, capture.getOutputSoFar().trim());
    }
    
    @Test
    public void testStdoutCaptureTeeDetail() {
        ThreadLocalPrintStream.stdout();
        System.out.println("1 - not captured, but next go to capture and stdout");
        OutputCapturingContext capture1 = ThreadLocalPrintStream.stdout().captureTee();
        OutputCapturingContext capture2 = ThreadLocalPrintStream.stdout().captureTee();
        final String TWO = "2 - captured";
        System.out.println(TWO);
        Assert.assertEquals(TWO, capture1.getOutputSoFar().trim());
        Assert.assertEquals(TWO, capture2.getOutputSoFar().trim());
        String out2 = capture2.end();
        
        final String THREE = "3 - captured by 1";
        System.out.println(THREE);
        String out1 = capture1.end();
        
        System.out.println("4 - not captured, restored normal output");
        Assert.assertEquals(TWO, out2.trim());
        Assert.assertEquals(TWO+"\n"+THREE, out1.trim());
    }

}
