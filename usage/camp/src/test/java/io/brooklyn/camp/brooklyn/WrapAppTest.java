package io.brooklyn.camp.brooklyn;

import java.io.StringReader;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.StartableApplication;

public class WrapAppTest extends AbstractYamlTest {
    private static final String NO_WRAP_APP_IMPLICIT =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String NO_WRAP_APP_EXPLICIT =
            "name: Empty App\n" +
            "wrappedApp: false\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String WRAP_APP_IMPLICIT =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication\n" +
            "   - type: brooklyn.test.entity.TestApplication";
        
    private static final String WRAP_APP_EXPLICIT =
            "name: Empty App\n" +
            "wrappedApp: true\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestApplication";
    
    private static final String WRAP_ENTITY =
            "name: Empty App\n" +
            "services:\n" +
            "   - type: brooklyn.test.entity.TestEntity";
    
    @Test
    public void testNoWrapAppImplicit() throws Exception {
        StartableApplication app = createApp(NO_WRAP_APP_IMPLICIT);
        Assert.assertTrue(app.getChildren().size() == 0);
    }
    
    @Test
    public void testNoWrapAppExplicit() throws Exception {
        StartableApplication app = createApp(NO_WRAP_APP_EXPLICIT);
        Assert.assertTrue(app.getChildren().size() == 0);
    }
    
    @Test
    public void testWrapAppImplicit() throws Exception {
        StartableApplication app = createApp(WRAP_APP_IMPLICIT);
        Assert.assertTrue(app.getChildren().size() == 2);
    }
    
    @Test
    public void testWrapAppExplicit() throws Exception {
        StartableApplication app = createApp(WRAP_APP_EXPLICIT);
        Assert.assertTrue(app.getChildren().size() == 1);
    }
    
    @Test
    public void testWrapEntity() throws Exception {
        StartableApplication app = createApp(WRAP_ENTITY);
        Assert.assertTrue(app.getChildren().size() == 1);
    }
    
    private StartableApplication createApp(String yaml) throws Exception {
        StringReader in = new StringReader(yaml);
        StartableApplication app = (StartableApplication)createAndStartApplication(in);
        in.close();
        return app;
    }
}
