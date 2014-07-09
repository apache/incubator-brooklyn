package brooklyn.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BrooklynFeatureEnablementTest {

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        BrooklynFeatureEnablement.clearCache();
    }
    
    @Test
    public void testDefaultIsNotEnabled() throws Exception {
        assertFalse(BrooklynFeatureEnablement.isEnabled("feature.not.referenced.anywhere"));
    }
    
    @Test
    public void testCanSetPropertyEnablement() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetPropertyEnablement";
        boolean preTestVal = BrooklynFeatureEnablement.isEnabled(featureProperty);
        try {
            boolean oldVal = BrooklynFeatureEnablement.enable(featureProperty);
            assertEquals(oldVal, preTestVal);
            assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
            
            boolean oldVal2 = BrooklynFeatureEnablement.disable(featureProperty);
            assertTrue(oldVal2);
            assertFalse(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            BrooklynFeatureEnablement.setEnablement(featureProperty, preTestVal);
        }
    }
    
    @Test
    public void testReadsEnablementFromProperties() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testReadsEnablementFromProperties";
        System.setProperty(featureProperty, "true");
        try {
            assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            System.clearProperty(featureProperty);
        }
    }
    
    @Test
    public void testCanSetDefaultWhichTakesEffectIfNoSystemProperty() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetDefaultWhichTakesEffectIfNoSystemProperty";
        BrooklynFeatureEnablement.setDefault(featureProperty, true);
        assertTrue(BrooklynFeatureEnablement.isEnabled(featureProperty));
        System.setProperty(featureProperty, "true");
        try {
        } finally {
            System.clearProperty(featureProperty);
        }
    }
    
    @Test
    public void testCanSetDefaultWhichIsIgnoredIfSystemProperty() throws Exception {
        String featureProperty = "brooklyn.experimental.feature.testCanSetDefaultWhichIsIgnoredIfSystemProperty";
        System.setProperty(featureProperty, "false");
        try {
            BrooklynFeatureEnablement.setDefault(featureProperty, true);
            assertFalse(BrooklynFeatureEnablement.isEnabled(featureProperty));
        } finally {
            System.clearProperty(featureProperty);
        }
    }
}
