package brooklyn.util.internal;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.javalang.JavaClassNames;

/** test must not be in {@link JavaClassNames} directory due to exclusion! */
public class JavaClassNamesCallerTest {

    @Test
    public void testCallerIsMe() {
        String result = JavaClassNames.niceClassAndMethod();
        Assert.assertEquals(result, "JavaClassNamesCallerTest.testCallerIsMe");
    }

    @Test
    public void testCallerIsYou() {
        other();
    }
    
    public void other() {
        String result = JavaClassNames.callerNiceClassAndMethod(1);
        Assert.assertEquals(result, "JavaClassNamesCallerTest.testCallerIsYou");
    }
    

}
