package brooklyn.management.internal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.management.ExecutionContext
import brooklyn.management.Task

class EntityExecutionManagerTest {

    private static final int TIMEOUT = 10*1000
    
    class TestEntity extends AbstractEntity {
        public TestEntity(Map properties=[:]) {
            super(properties)
        }
        public ExecutionContext getExecutionContext() {
            return super.getExecutionContext();
        }
    }
    
    @Test
    public void testGetTasksOfEntity() throws Exception {
        AbstractApplication app = new AbstractApplication() {}
        TestEntity e = new TestEntity([owner:app])
        
        CountDownLatch latch = new CountDownLatch(1)
        Task task = e.executionContext.submit( { latch.countDown() } )
        latch.await(TIMEOUT, TimeUnit.MILLISECONDS)
        
        Collection<Task> tasks = app.managementContext.executionManager.getTasksWithTag(e);
        Assert.assertEquals(tasks, [task])
    }
}
