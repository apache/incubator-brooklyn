package org.overpaas.policy;

import java.util.Collections

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

import org.overpaas.activity.EventDictionary
import org.overpaas.activity.impl.EventImpl
import org.overpaas.activity.impl.NestedMapAccessorImpl


public class BalancerPolicyTest extends GroovyTestCase {

    private BalanceableEntity entity;
    private BalancerPolicy balancerPolicy;
    private Entity container1;
    private Entity container2;
    private MoveableEntity itemA;
    private MoveableEntity itemB;
    private MoveableEntity itemC;
    
    @Before
    public void setUp() throws Exception {
        entity = Mockito.mock(BalanceableEntity.class);
        container1 = Mockito.mock(Entity.class);
        container2 = Mockito.mock(Entity.class);
        itemA = Mockito.mock(MoveableEntity.class);
        itemB = Mockito.mock(MoveableEntity.class);
        itemC = Mockito.mock(MoveableEntity.class);
        balancerPolicy = new BalancerPolicy(entity);
    }
    
    @Test
    public void testEmitsTooHotWhenSingleContainerIsTooHot() throws Exception {
        balancerPolicy.setBalanceableSubEntityWorkrateMetricName("totalWorkrate")
        balancerPolicy.setMoveableEntityWorkrateMetricName("itemWorkrate")
        balancerPolicy.setMaxWatermark(10)
        
        Mockito.when(entity.getBalanceableSubContainers()).thenReturn(Collections.singleton(container1));
        Mockito.when(container1.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":11]));
        
        balancerPolicy.onEvent(null); // TODO currently this triggers the check...
        
        def expectedEvent = new EventImpl(EventDictionary.TOO_HOT_EVENT_NAME, ["average":11/1, "size":1, "coldest":11, "hottest":11])
        Mockito.verify(entity).raiseEvent(expectedEvent);
    }
    
    @Test
    public void testEmitsTooHotWhenMultipleContainersTooHot() throws Exception {
        balancerPolicy.setBalanceableSubEntityWorkrateMetricName("totalWorkrate")
        balancerPolicy.setMoveableEntityWorkrateMetricName("itemWorkrate")
        balancerPolicy.setMaxWatermark(10)
        
        Mockito.when(entity.getBalanceableSubContainers()).thenReturn(Arrays.asList(container1, container2));
        Mockito.when(entity.getMovableItems()).thenReturn(Arrays.asList(itemA, itemB, itemC));
        Mockito.when(entity.getMovableItemsAt(container1)).thenReturn(Arrays.asList(itemA, itemB));
        Mockito.when(entity.getMovableItemsAt(container2)).thenReturn(Arrays.asList(itemC));
        Mockito.when(container1.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":24]));
        Mockito.when(container2.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":12]));
        Mockito.when(itemA.getMetrics()).thenReturn(new NestedMapAccessorImpl(["itemWorkrate":12]));
        Mockito.when(itemB.getMetrics()).thenReturn(new NestedMapAccessorImpl(["itemWorkrate":12]));
        Mockito.when(itemC.getMetrics()).thenReturn(new NestedMapAccessorImpl(["itemWorkrate":12]));
        
        balancerPolicy.onEvent(null); // TODO currently this triggers the check...

        def expectedEvent = new EventImpl(EventDictionary.TOO_HOT_EVENT_NAME, ["average":36/2, "size":2, "coldest":12, "hottest":24])
        Mockito.verify(entity).raiseEvent(expectedEvent);
        
        // TODO Assert that move not called; but can't use verifyNoMoreInteractions 
        // because that asserts getBalanceableSubContainers only called once!
        //Mockito.verifyNoMoreInteractions(entity);
    }
    
    @Test
    public void testEmitsTooColdWhenMultipleColdContainers() throws Exception {
        balancerPolicy.setBalanceableSubEntityWorkrateMetricName("totalWorkrate")
        balancerPolicy.setMoveableEntityWorkrateMetricName("itemWorkrate")
        balancerPolicy.setMinWatermark(10)
        
        Mockito.when(entity.getBalanceableSubContainers()).thenReturn(Arrays.asList(container1, container2));
        Mockito.when(entity.getMovableItems()).thenReturn(Collections.emptyList());
        Mockito.when(entity.getMovableItemsAt(container1)).thenReturn(Collections.emptyList());
        Mockito.when(entity.getMovableItemsAt(container2)).thenReturn(Collections.emptyList());
        Mockito.when(container1.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":0]));
        Mockito.when(container2.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":0]));
        
        balancerPolicy.onEvent(null); // TODO currently this triggers the check...

        def expectedEvent = new EventImpl(EventDictionary.TOO_COLD_EVENT_NAME, ["average":0/2, "size":2, "coldest":0, "hottest":0])
        Mockito.verify(entity).raiseEvent(expectedEvent);
    }
    
    @Test
    public void testBalancesLoadFromHottestToColdest() throws Exception {
        balancerPolicy.setBalanceableSubEntityWorkrateMetricName("totalWorkrate")
        balancerPolicy.setMoveableEntityWorkrateMetricName("itemWorkrate")
        
        Mockito.when(entity.getBalanceableSubContainers()).thenReturn(Arrays.asList(container1, container2));
        Mockito.when(entity.getMovableItems()).thenReturn(Arrays.asList(itemA, itemB));
        Mockito.when(entity.getMovableItemsAt(container1)).thenReturn(Arrays.asList(itemA, itemB));
        Mockito.when(entity.getMovableItemsAt(container2)).thenReturn(Arrays.asList());
        Mockito.when(container1.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":10]));
        Mockito.when(container2.getMetrics()).thenReturn(new NestedMapAccessorImpl(["totalWorkrate":0]));
        Mockito.when(itemA.getMetrics()).thenReturn(new NestedMapAccessorImpl(["itemWorkrate":6]));
        Mockito.when(itemB.getMetrics()).thenReturn(new NestedMapAccessorImpl(["itemWorkrate":4]));
        
        balancerPolicy.onEvent(null); // TODO currently this triggers the check...
        
        Mockito.verify(entity).move(itemB, container2);
    }
}
