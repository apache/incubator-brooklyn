package brooklyn.catalog.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.maven.MavenRetriever;

public class CatalogDtoTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogDtoTest.class);

    private LocalManagementContext managementContext;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test(groups="Integration")
    public void testCatalogLookup() {
        CatalogDto root = buildExampleCatalog();
        checkCatalogHealthy(root);
    }
    
    @Test(groups="Integration")
    public void testCatalogSerializeAndLookup() {
        CatalogDto root = buildExampleCatalog();
        CatalogXmlSerializer serializer = new CatalogXmlSerializer();
        
        String xml = serializer.toString(root);
        log.info("Example catalog serialized as:\n"+xml);
        
        CatalogDto root2 = (CatalogDto) serializer.fromString(xml);
        checkCatalogHealthy(root2);
    }

    protected void checkCatalogHealthy(CatalogDto root) {
        Assert.assertEquals(root.catalogs.size(), 4);
        CatalogDo loader = new CatalogDo(root).load(managementContext, null);
        
        // test app comes from jar, by default
        CatalogItemDo<?,?> worker = loader.getCache().get(TestApplication.class.getCanonicalName());
        Assert.assertNotNull(worker);
        Assert.assertEquals(worker.getName(), "Test App from JAR");
        
        // TODO can test scanned elements, links to other catalogs, etc
    }

    public static CatalogDto buildExampleCatalog() {
        CatalogDo root = new CatalogDo(CatalogDto.newNamedInstance("My Local Catalog", 
                "My favourite local settings, including remote catalogs -- " +
        		"intended partly as a teaching example for what can be expressed, and how"));
        root.setClasspathScanForEntities(CatalogScanningModes.NONE);
        
        CatalogDo testEntitiesJavaCatalog = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from Java", null));
        testEntitiesJavaCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        testEntitiesJavaCatalog.addToClasspath(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalog.addEntry(CatalogItemDtoAbstract.newTemplateFromJava(
                TestApplication.class.getCanonicalName(), "Test App from JAR"));
        testEntitiesJavaCatalog.addEntry(CatalogItemDtoAbstract.newEntityFromJava(
                TestEntity.class.getCanonicalName(), "Test Entity from JAR"));
        root.addCatalog(testEntitiesJavaCatalog.dto);

        CatalogDo testEntitiesJavaCatalogScanning = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from Java Scanning", null));
        testEntitiesJavaCatalogScanning.addToClasspath(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalogScanning.setClasspathScanForEntities(CatalogScanningModes.ANNOTATIONS);
        root.addCatalog(testEntitiesJavaCatalogScanning.dto);
        
        CatalogDo osgiCatalog = new CatalogDo(CatalogDto.newNamedInstance("Test Entities from OSGi",
                "A catalog whose entries define their libraries as a list of OSGi bundles"));
        osgiCatalog.setClasspathScanForEntities(CatalogScanningModes.NONE);
        CatalogEntityItemDto osgiEntity = CatalogItemDtoAbstract.newEntityFromJava(TestEntity.class.getCanonicalName(), "Test Entity from OSGi");
        // NB: this is not actually an OSGi bundle, but it's okay as we don't instantiate the bundles ahead of time (currently)
        osgiEntity.libraries.addBundle(MavenRetriever.localUrl(BrooklynMavenArtifacts.artifact("", "brooklyn-core", "jar", "tests")));
        testEntitiesJavaCatalog.addEntry(osgiEntity);
        root.addCatalog(osgiCatalog.dto);

        root.addCatalog(CatalogDto.newLinkedInstance("classpath://brooklyn-catalog-empty.xml"));
        return root.dto;
    }

}

