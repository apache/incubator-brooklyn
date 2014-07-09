package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.Task;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.DynamicTasks;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class RebindManagerTest extends RebindTestFixtureWithApp {

    @Test
    public void testRebindingEntityCanCallTask() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(TestEntityWithTaskInRebind.class));
        
        newApp = rebind();
        Entity newEntity = Iterables.find(newApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        assertEquals(newEntity.getAttribute(TestEntity.NAME), "abc");
    }
    public static class TestEntityWithTaskInRebind extends TestEntityImpl {
        @Override
        public void rebind() {
            super.rebind();
            Task<String> task = new BasicTask<String>(new Callable<String>() {
                @Override public String call() {
                    return "abc";
                }});
            String val = DynamicTasks.queueIfPossible(task)
                    .orSubmitAsync()
                    .asTask()
                    .getUnchecked();
            setAttribute(TestEntity.NAME, val);
        }
    }
}
