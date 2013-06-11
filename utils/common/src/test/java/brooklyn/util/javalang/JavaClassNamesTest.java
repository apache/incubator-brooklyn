package brooklyn.util.javalang;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JavaClassNamesTest {

    @Test
    public void testType() {
        Assert.assertEquals(JavaClassNames.type(this), JavaClassNamesTest.class);
        Assert.assertEquals(JavaClassNames.type(JavaClassNamesTest.class), JavaClassNamesTest.class);
    }
    
    @Test
    public void testPackage() {
        Assert.assertEquals(JavaClassNames.packageName(JavaClassNamesTest.class), "brooklyn.util.javalang");
        Assert.assertEquals(JavaClassNames.packagePath(JavaClassNamesTest.class), "/brooklyn/util/javalang/");
    }
    
    @Test
    public void resolveName() {
        Assert.assertEquals(JavaClassNames.resolveName(this, "foo.txt"), "/brooklyn/util/javalang/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(JavaClassNamesTest.class, "foo.txt"), "/brooklyn/util/javalang/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "/foo.txt"), "/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "/bar/foo.txt"), "/bar/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, "bar/foo.txt"), "/brooklyn/util/javalang/bar/foo.txt");
        Assert.assertEquals(JavaClassNames.resolveName(this, null), null);
    }
    
}
