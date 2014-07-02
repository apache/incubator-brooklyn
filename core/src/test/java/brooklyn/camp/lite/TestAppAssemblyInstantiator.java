package brooklyn.camp.lite;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.BasicAssemblyTemplateInstantiator;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

/** simple illustrative instantiator which always makes a {@link TestApplication}, populated with {@link TestEntity} children,
 * all setting {@link TestEntity#CONF_NAME} for the name in the plan and in the service specs
 * <p>
 * the "real" instantiator for brooklyn is in brooklyn-camp project, not visible here, so let's have something we can test */
public class TestAppAssemblyInstantiator extends BasicAssemblyTemplateInstantiator {

    protected final LocalManagementContext mgmt;
    
    public TestAppAssemblyInstantiator(LocalManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    @Override
    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        TestApplication app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class)
            .configure(TestEntity.CONF_NAME, template.getName())
            .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", template.getType(), "desc", template.getDescription()))
            , mgmt);
        for (ResolvableLink<PlatformComponentTemplate> t: template.getPlatformComponentTemplates().links()) {
            app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, t.getName())
                .configure(TestEntity.CONF_MAP_THING, MutableMap.of("type", t.resolve().getType(), "desc", t.resolve().getDescription()))
                );
        }
        return new TestAppAssembly(app);
    }

}
