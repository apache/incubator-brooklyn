package brooklyn.entity;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.DefaultValue;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Effectors;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorMetadataTest {
    
    private TestApplication app;
    private MyAnnotatedEntity e1;
    private MyOverridingEntity e2;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e1 = app.createAndManageChild(EntitySpec.create(MyAnnotatedEntity.class));
        e2 = app.createAndManageChild(EntitySpec.create(MyOverridingEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testEffectorMetaDataFromDeprecatedAnnotations() {
        Effector<?> effector = findEffector(e1, "effWithOldAnnotation");
        assertEquals(effector, MyAnnotatedEntity.EFF_WITH_OLD_ANNOTATION);
        assertEquals(effector.getName(), "effWithOldAnnotation");
        assertEquals(effector.getDescription(), "my effector description");
        assertEquals(effector.getReturnType(), String.class);
        assertParametersEqual(
                effector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<String>("param1", String.class, "my param description", "my default val")));
    }

    @Test
    public void testEffectorMetaDataFromNewAnnotationsWithConstant() {
        Effector<?> effector = findEffector(e1, "effWithNewAnnotation");
        Assert.assertTrue(Effectors.sameSignature(effector, MyAnnotatedEntity.EFF_WITH_NEW_ANNOTATION));
        assertEquals(effector.getName(), "effWithNewAnnotation");
        assertEquals(effector.getDescription(), "my effector description");
        assertEquals(effector.getReturnType(), String.class);
        assertParametersEqual(
                effector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<String>("param1", String.class, "my param description", "my default val")));
    }

    @Test
    public void testEffectorMetaDataFromNewAnnotationsWithoutConstant() {
        Effector<?> effector = findEffector(e1, "effWithAnnotationButNoConstant");
        assertEquals(effector.getName(), "effWithAnnotationButNoConstant");
        assertEquals(effector.getDescription(), "my effector description");
        assertEquals(effector.getReturnType(), String.class);
        assertParametersEqual(
                effector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<String>("param1", String.class, "my param description", "my default val")));
    }

    @Test
    public void testEffectorMetaDataFromOverriddenMethod() {
        // Overridden with new annotations
        Effector<?> startEffector = findEffector(e2, "start");
        assertEquals(startEffector.getName(), "start");
        assertEquals(startEffector.getDescription(), "My overridden start description");
        assertEquals(startEffector.getReturnType(), void.class);
        assertParametersEqual(
                startEffector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<Collection>("locations2", Collection.class, "my overridden param description", null)));
    }

    private Effector<?> findEffector(Entity entity, String effectorName) {
        Set<Effector<?>> effectors = entity.getEntityType().getEffectors();
        for (Effector<?> effector : effectors) {
            if (effector.getName().equals(effectorName)) {
                return effector;
            }
        }
        throw new NoSuchElementException("No effector with name "+effectorName+" (contenders "+effectors+")");
    }

    private void assertParametersEqual(List<ParameterType<?>> actuals, List<ParameterType<?>> expecteds) {
        assertEquals(actuals.size(), expecteds.size(), "actual="+actuals);
        for (int i = 0; i < actuals.size(); i++) {
            ParameterType<?> actual = actuals.get(i);
            ParameterType<?> expected = expecteds.get(i);
            assertParameterEqual(actual, expected);
        }
    }
    
    private void assertParameterEqual(ParameterType<?> actual, ParameterType<?> expected) {
        assertEquals(actual.getName(), expected.getName(), "actual="+actual);
        assertEquals(actual.getDescription(), expected.getDescription(), "actual="+actual);
        assertEquals(actual.getParameterClass(), expected.getParameterClass(), "actual="+actual);
        assertEquals(actual.getParameterClassName(), expected.getParameterClassName(), "actual="+actual);
    }

    @ImplementedBy(MyAnnotatedEntityImpl.class)
    public interface MyAnnotatedEntity extends Entity {
    	static MethodEffector<String> EFF_WITH_OLD_ANNOTATION = new MethodEffector<String>(MyAnnotatedEntity.class, "effWithOldAnnotation");
        static MethodEffector<String> EFF_WITH_NEW_ANNOTATION = new MethodEffector<String>(MyAnnotatedEntity.class, "effWithNewAnnotation");

        @Description("my effector description")
        public String effWithOldAnnotation(
                @NamedParameter("param1") @DefaultValue("my default val") @Description("my param description") String param1);
        
        @brooklyn.entity.annotation.Effector(description="my effector description")
        public String effWithNewAnnotation(
                @EffectorParam(name="param1", defaultValue="my default val", description="my param description") String param1);
        
        @brooklyn.entity.annotation.Effector(description="my effector description")
        public String effWithAnnotationButNoConstant(
                @EffectorParam(name="param1", defaultValue="my default val", description="my param description") String param1);
    }
    
    public static class MyAnnotatedEntityImpl extends AbstractEntity implements MyAnnotatedEntity {
        @Override
        public String effWithOldAnnotation(String param1) {
            return param1;
        }

        @Override
        public String effWithNewAnnotation(String param1) {
            return param1;
        }

        @Override
        public String effWithAnnotationButNoConstant(String param1) {
            return param1;
        }
    }
    
    @ImplementedBy(MyOverridingEntityImpl.class)
    public interface MyOverridingEntity extends Entity, Startable {
        @Override
        @brooklyn.entity.annotation.Effector(description="My overridden start description")
        void start(@EffectorParam(name="locations2", description="my overridden param description") Collection<? extends Location> locations2);
    }

    public static class MyOverridingEntityImpl extends AbstractEntity implements MyOverridingEntity {

        @Override
        public void restart() {
        }

        @Override
        public void start(Collection<? extends Location> locations2) {
        }

        @Override
        public void stop() {
        }
    }
}
