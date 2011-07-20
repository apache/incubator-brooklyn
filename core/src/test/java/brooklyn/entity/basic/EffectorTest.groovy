package brooklyn.entity.basic;

import java.util.concurrent.TimeUnit;

import groovy.transform.InheritConstructors

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.entity.Effector
import brooklyn.management.Task

public class EffectorTest {

    private static final long TIMEOUT = 10*1000
    
    @InheritConstructors
    public class MyEntity extends AbstractEntity {

        public static Effector<String> CONCATENATE = new EffectorInferredFromAnnotatedMethod<Void>(MyEntity.class, "concatenate", "My effector");
    
        public MyEntity(Map flags) {
            super(flags)
        }
        
        /**
         * Start the entity in the given collection of locations.
         */
        String concatenate(@NamedParameter("first") @Description("Locations to start entity in") String first,
            @NamedParameter("second") @Description("Locations to start entity in") String second) {
            return first+second
        }
    }
            
    @Test
    public void testCanInvokeEffector() {
        AbstractApplication app = new AbstractApplication() {}
        MyEntity e = new MyEntity([owner:app])
        Task<String> task = e.invoke(MyEntity.CONCATENATE, [first:"a",second:"b"])
        Assert.assertEquals(task.get(TIMEOUT, TimeUnit.MILLISECONDS), "ab")
    }
}
