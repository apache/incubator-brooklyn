package brooklyn.util.javalang;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EnumsTest {

    private static enum SomeENum { E_300, E_624 }
    
    @Test
    public static void testAllValuesEnumerated() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "E_624");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover values.*vitamin.*")
    public static void testAllValuesEnumeratedExtra() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "E_624", "Vitamin C");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover enums.*e_624.*leftover values.*")
    public static void testAllValuesEnumeratedMissing() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300");
    }

    @Test(expectedExceptions=IllegalStateException.class, expectedExceptionsMessageRegExp = ".*leftover enums.*e_624.*leftover values.*msg.*")
    public static void testAllValuesEnumeratedMissingAndExtra() {
        Enums.checkAllEnumeratedIgnoreCase(SomeENum.class, "e_300", "MSG");
    }

    @Test
    public static void testValueOf() {
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "e_300").get(), SomeENum.E_300);
        Assert.assertEquals(Enums.valueOfIgnoreCase(SomeENum.class, "e_624").get(), SomeENum.E_624);
        Assert.assertFalse(Enums.valueOfIgnoreCase(SomeENum.class, "MSG").isPresent());
    }

}
