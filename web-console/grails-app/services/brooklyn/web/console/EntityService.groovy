package brooklyn.web.console

import brooklyn.entity.Entity
import brooklyn.web.console.entity.SensorSummary
import brooklyn.event.Sensor
import brooklyn.entity.Effector
import brooklyn.web.console.entity.TaskSummary
import brooklyn.event.SensorEvent
import brooklyn.management.SubscriptionHandle
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import brooklyn.event.SensorEventListener
import java.util.concurrent.ConcurrentLinkedQueue
import brooklyn.event.AttributeSensor

class EntityService {

    static transactional = false
    def managementContextService

    private static final int CACHE_LIMIT = 10
    ConcurrentMap<String, ConcurrentMap<String, SensorSummary>> sensorCache = new ConcurrentHashMap<String, ConcurrentMap<String, SensorSummary>>()
    ConcurrentMap<String, SubscriptionHandle> subscriptions = new ConcurrentHashMap<String, SubscriptionHandle>()

    ConcurrentLinkedQueue<String> cacheQueue = new ConcurrentLinkedQueue<String>();

    public static class NoSuchEntity extends Exception {}

    public Collection<TaskSummary> getTasksOfEntity(String entityId) {
        return managementContextService.executionManager.getTasksWithTag(getEntity(entityId)).collect { new TaskSummary(it) }
    }

    private synchronized void unsubscribeEntitySensors(){
        String oldestEntity = cacheQueue.poll()
        if(managementContextService.subscriptionManager.unsubscribe(subscriptions.get(oldestEntity))){
            sensorCache.remove(oldestEntity)
            subscriptions.remove(oldestEntity)
        }
    }

    private void initializeEntitySensors(Entity entity) {
        synchronized (entity) {
            if(sensorCache.size() >= CACHE_LIMIT){
                unsubscribeEntitySensors()
            }

            sensorCache.putIfAbsent(entity.id, new ConcurrentHashMap<String, SensorSummary>())
            for (Sensor s : entity.entityClass.sensors) {
                if (s instanceof AttributeSensor) {
                    sensorCache[entity.id].putIfAbsent(s.name, new SensorSummary(s, entity.getAttribute(s)))
                }
            }

            if (!subscriptions.containsKey(entity.id)) {
                SubscriptionHandle handle = managementContextService.subscriptionManager.subscribe(entity, null,
                    new SensorEventListener() {
                        void onEvent(SensorEvent event) {
                            sensorCache.putIfAbsent(event.source.id, new ConcurrentHashMap<String, SensorSummary>())
                            sensorCache[event.source.id].put(event.sensor.name, new SensorSummary(event))
                        }
                    })
                cacheQueue.add(entity.id)
                subscriptions.put(entity.id, handle)
            }
        }
    }

    public Collection<SensorSummary> getSensorData(String entityId) {
        Entity entity = getEntity(entityId)
        if (!entity) throw new NoSuchEntity()

        // FIXME subscription not working. Removing that for the demo...
        // Needs retested; it could have just been the bug in EntityController calling getSensorsOfEntity instead of getSensorData
        
        Collection<SensorSummary> result = []
        for (Sensor s : entity.entityClass.sensors) {
            if (s instanceof AttributeSensor) {
                result.add(new SensorSummary(s, entity.getAttribute(s)))
            }
        }
        return result
        
//        if (!sensorCache.containsKey(entityId) || sensorCache[entityId].isEmpty()) {
//            initializeEntitySensors(entity)
//        }
//        return sensorCache[entityId].values()
    }

    public Collection<Effector> getEffectorsOfEntity(String entityId) {
        Set<Effector> results = []
        Entity entity = getEntity(entityId)
        if (entity) {
            results.addAll(entity.entityClass.effectors)
        }

        return results
    }

    public Collection<Entity> getChildren(Entity parent) {
        return parent.ownedChildren
    }

    public List<Entity> getAncestorsOf(Entity child) {
        List<Entity> result = []
        Entity ancestor = child.getOwner()
        while (ancestor) {
            result.add(ancestor)
            ancestor = ancestor.getOwner()
        }
        return result
    }
    

    public boolean isChildOf(Entity child, Collection<Entity> parents) {
        parents.find { parent ->
            getChildren(parent).contains(child) || isChildOf(child, getChildren(parent))
        }
    }

    public Collection<Entity> getTopLevelEntities() {
        return managementContextService.applications
    }

    public Collection<Entity> getAllEntities() {
        return flattenEntities(getTopLevelEntities());
    }

    private Set<Entity> flattenEntities(Collection<Entity> entities) {
        Set<Entity> flattenedList = []
        entities.each {
            e ->
            flattenedList.add(e)
            getChildren(e).each {
                flattenedList.addAll(flattenEntities([it]))
            }
        }
        flattenedList
    }

    /** Returns entities which match the given conditions.
     *
     * If the condition value is false entities will not be filtered on the value.
     * Otherwise, the condition is a regular expression that will be matched
     * against the corresponding field of the entities.
     */
    public Set<Entity> getEntitiesMatchingCriteria(String name, String id, String applicationId) {
        getAllEntities().findAll {
            it ->
            ((!name || it.displayName.toLowerCase() =~ name.toLowerCase())
                    && (!id || it.id == id)
                    && (!applicationId || it.application.id =~ applicationId)
            )
        }
    }

    public Entity getEntity(String id, name = null, applicationId = null) {
        Set<Entity> entities = getEntitiesMatchingCriteria(null, id, null)
        if (entities.size() == 1) {
            return entities.iterator().next()
        }
        return null
    }

    private List<Entity> leaves(Entity e) {
        def children = getChildren(e);

        if (children.size() == 0) return e;
        // inject is foldl
        return children.collect { leaves(it) }.inject([]) { a, b -> a + b }
    }

    public List<Entity> getAllLeafEntities(List<Entity> es) {
        return children.collect { leaves(it) }.inject([]) { a, b -> a + b }
    }

    private Location getNearestAncestorWithCoordinates(Location l) {
        // if l has coords, return l
        // else try parent
        throw new Exception("todo");
    }

    /* Returns the number of entites at each location for which the geographic coordinates are known. */
    public Map<Location, Int> entityCountsAtLocatedLocations() {
        throw new Exception("todo");
    }
}
