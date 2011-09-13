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
import brooklyn.location.Location
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.policy.Policy
import brooklyn.policy.basic.GeneralPurposePolicy
import brooklyn.management.internal.AbstractManagementContext

public class EntityService {
    static transactional = false
    def managementContextService

    private static final int CACHE_LIMIT = 10

    ConcurrentMap<String, ConcurrentMap<String, SensorSummary>> sensorCache =
        new ConcurrentHashMap<String, ConcurrentMap<String, SensorSummary>>()
    ConcurrentMap<String, SubscriptionHandle> subscriptions = new ConcurrentHashMap<String, SubscriptionHandle>()

    ConcurrentLinkedQueue<String> cacheQueue = new ConcurrentLinkedQueue<String>();

    public static class NoSuchEntity extends Exception {}

    // TODO Should this return Task objects, and let the EntityController convert them to TaskSummary?
    // TODO Want to handle pagination better; for now we just restrict list to 20 most recent
    public List<TaskSummary> getTasksOfAllEntities() {
        final int MAX_NUM_RETURNED = 20
        
        List<TaskSummary> result = managementContextService.executionManager.getTasksWithAllTags(
                [AbstractManagementContext.EFFECTOR_TAG]).collect { new TaskSummary(it) }
                
        Collections.sort(result, {TaskSummary t1, TaskSummary t2 -> 
                return new Long(t2.rawSubmitTimeUtc - t1.rawSubmitTimeUtc).intValue() } as Comparator)
        
        return result.subList(0, Math.min(MAX_NUM_RETURNED, result.size()))
    }

    // TODO Should this return Task objects, and let the EntityController convert them to TaskSummary?
    public Collection<TaskSummary> getTasksOfEntity(String entityId) {
        Entity e = getEntity(entityId)
        if (!e) throw new NoSuchEntity()

        return managementContextService.executionManager.getTasksWithAllTags(
                [e, AbstractManagementContext.EFFECTOR_TAG]).collect { new TaskSummary(it) }
    }

    private void unsubscribeEntitySensors() {
        String oldestEntity = cacheQueue.poll()
        if (oldestEntity && managementContextService.subscriptionManager.unsubscribe(subscriptions.get(oldestEntity))) {
            sensorCache.remove(oldestEntity)
            subscriptions.remove(oldestEntity)
        }
    }

    private void initializeEntitySensors(Entity entity) {
        synchronized (entity) {
            if (sensorCache.size() >= CACHE_LIMIT){
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

        if (!sensorCache.containsKey(entityId) || sensorCache[entityId].isEmpty()) {
            initializeEntitySensors(entity)
        }
        return sensorCache[entityId].values()
    }

    public Collection<Effector> getEffectorsOfEntity(String entityId) {
        Entity entity = getEntity(entityId)
        if (!entity) throw new NoSuchEntity()
        
        return entity.entityClass.effectors
    }

    public Collection<Policy> getPoliciesOfEntity(Entity entity) {
        if (!entity) throw new NoSuchEntity()

        entity.policies
    }

    public Policy getPolicyOfEntity(Entity entity, String policyId){
        Policy policy
        if(entity.policies != null){
            policy = entity.policies.find {
                it.id.equals(policyId)
            }
        }
        return policy
    }

    public void executePolicyAction(String action, Policy policy, Entity entity){
        if(action == 'suspend'){
            policy.suspend()
        }
        else if(action == 'resume'){
            policy.resume()
        }
        else {
            policy.destroy()
            entity.removePolicy(policy)
        }

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
            parent.getOwnedChildren().contains(child) || isChildOf(child, parent.getOwnedChildren())
        }
    }

    public Collection<Entity> getTopLevelEntities() {
        return managementContextService.applications
    }

    /* List of all locations configured in all applications. */
    public List<Entity> applicationLocations() {
        def apps = managementContextService.getApplications();
        return apps.collect {it.locations}.flatten().unique()
    }

    public Collection<Entity> getAllEntities() {
        return flattenEntities(getTopLevelEntities());
    }

    private Set<Entity> flattenEntities(Collection<Entity> entities) {
        Set<Entity> flattenedList = []
        entities.each {
            e ->
            flattenedList.add(e)
            e.getOwnedChildren().each {
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
        Collection<Entity> children = e.getOwnedChildren();
        if (children.size() == 0) return [e];
        // inject is foldl
        return children.collect { leaves(it) }.flatten()
    }

    public List<Entity> getAllLeafEntities(Collection<Entity> es) {
        return es.collect { leaves(it) }.flatten()
    }

    private Location getNearestAncestorWithCoordinates(Location l) {
        if (l == null) return null;
        if (l.getLocationProperty("latitude") && l.getLocationProperty("longitude")) return l;
        return getNearestAncestorWithCoordinates(l.getParentLocation());
    }

    /* Returns the number of entites at each location for which the geographic coordinates are known. */
    public Map<Location, Integer> entityCountsAtLocatedLocations() {
        Map<Location, Integer> cs = [:]
        
        List<Entity> es = getAllLeafEntities(getTopLevelEntities())

        // Will count once for each location of an entity. This probably makes sense but maybe should only count as a fraction
        // of an entity in each place.
        List<Location> ls =
            // a list of lists of locations
            (es.collect {
            // a list of locations
            it.getLocations().collect {
                getNearestAncestorWithCoordinates(it)
            }})
            // collapse into a list of locations
            .flatten();

        ls.each {
            if (it != null) {
                if (cs[it] == null) cs[it] = 0;
                cs[it]++;
            }
        }

        return cs;
    }

    public List<Entity> getEntitiesForLocation(String locationName){
        def locationEntities = []
        for(entity in getAllEntities()){
                for(location in entity.getLocations()){
                    if(location.name == locationName){
                        locationEntities += (entity.displayName)
                        break
                    }
                }
        }
        return locationEntities;
    }
}
