package brooklyn.test;

import java.util.Locale;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

public class FixedLocaleTest {
    private Locale defaultLocale;
    private Locale fixedLocale;
    
    public FixedLocaleTest() {
        this(Locale.UK);
    }
    
    public FixedLocaleTest(Locale fixedLocale) {
        this.fixedLocale = fixedLocale;
    }

    @BeforeClass
    public void setUp() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(fixedLocale);
    }
    
    @AfterClass
    public void tearDown() {
        Locale.setDefault(defaultLocale);
    }

}
