package org.apache.brooklyn.util.core.task;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.util.concurrent.Callables;

public class TaskPredicatesTest extends BrooklynAppUnitTestSupport {

    private ExecutionManager execManager;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        execManager = mgmt.getExecutionManager();
    }

    @Test
    public void testDisplayNameEqualTo() throws Exception {
        Task<Object> task = execManager.submit(TaskBuilder.builder()
                .body(Callables.<Object>returning("val"))
                .displayName("myname")
                .build());
        assertTrue(TaskPredicates.displayNameEqualTo("myname").apply(task));
        assertFalse(TaskPredicates.displayNameEqualTo("wrong").apply(task));
    }
    
    @Test
    public void testDisplayNameMatches() throws Exception {
        Task<Object> task = execManager.submit(TaskBuilder.builder()
                .body(Callables.<Object>returning("val"))
                .displayName("myname")
                .build());
        assertTrue(TaskPredicates.displayNameMatches(Predicates.equalTo("myname")).apply(task));
        assertFalse(TaskPredicates.displayNameMatches(Predicates.equalTo("wrong")).apply(task));
    }
}
