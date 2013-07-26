package brooklyn.config;

import static org.testng.Assert.assertEquals;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

@Test
public class BrooklynPropertiesBuilderTest {

    private File globalPropertiesFile;
    private File localPropertiesFile;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        globalPropertiesFile = File.createTempFile("global-brooklyn-properties-test", ".properties");
        localPropertiesFile = File.createTempFile("local-brooklyn-properties-test", ".properties");
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (globalPropertiesFile != null) globalPropertiesFile.delete();
        if (localPropertiesFile != null) localPropertiesFile.delete();
    }
    
    @Test
    public void testSetGlobalBrooklynPropertiesFile() throws Exception {
        String globalPropertiesContents = "brooklyn.mykey=myval";
        Files.write(globalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        
        BrooklynProperties props = new BrooklynProperties.Factory.Builder()
                .globalPropertiesFile(globalPropertiesFile.getAbsolutePath())
                .build();
        
        assertEquals(props.get("brooklyn.mykey"), "myval");
    }
    
    @Test
    public void testSetLocalBrooklynPropertiesFile() throws Exception {
        String globalPropertiesContents = "brooklyn.mykey=myvalglobal"+"\n"+
                "brooklyn.mykey2=myvalglobal2"+"\n";
        Files.write(globalPropertiesContents, globalPropertiesFile, Charsets.UTF_8);
        
        String localPropertiesContents = "brooklyn.mykey=myvaloverriding"+"\n"+
                "brooklyn.mykeyLocal=myvallocal2"+"\n";
        Files.write(localPropertiesContents, localPropertiesFile, Charsets.UTF_8);
        
        BrooklynProperties props = new BrooklynProperties.Factory.Builder()
                .globalPropertiesFile(globalPropertiesFile.getAbsolutePath())
                .localPropertiesFile(localPropertiesFile.getAbsolutePath())
                .build();
        
        assertEquals(props.get("brooklyn.mykey"), "myvaloverriding");
        assertEquals(props.get("brooklyn.mykey2"), "myvalglobal2");
        assertEquals(props.get("brooklyn.mykeyLocal"), "myvallocal2");
    }
}
