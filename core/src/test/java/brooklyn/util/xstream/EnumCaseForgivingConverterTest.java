package brooklyn.util.xstream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

public class EnumCaseForgivingConverterTest {

    public enum MyEnum {
        FOO,
        BaR;
    }
    
    @Test
    public void testFindsCaseInsensitive() throws Exception {
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "FOO"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "foo"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "Foo"), MyEnum.FOO);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "BAR"), MyEnum.BaR);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "bar"), MyEnum.BaR);
        assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "Bar"), MyEnum.BaR);
    }
    
    @Test
    public void testFailsIfNoMatch() throws Exception {
        try {
            assertEquals(EnumCaseForgivingConverter.resolve(MyEnum.class, "DoesNotExist"), MyEnum.BaR);
            fail();
        } catch (IllegalArgumentException e) {
            if (!e.toString().matches(".*No enum.*MyEnum.DOESNOTEXIST")) throw e;
        }
    }
}
