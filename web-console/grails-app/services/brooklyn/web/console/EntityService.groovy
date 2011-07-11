package brooklyn.web.console

import brooklyn.entity.Entity
import brooklyn.management.Task
import brooklyn.web.console.entity.SensorSummary
import brooklyn.event.Sensor
import brooklyn.entity.Effector
import brooklyn.web.console.entity.TaskSummary

class EntityService {

    static transactional = false
    def managementContextService

    public Collection<TaskSummary> getTasksOfEntity(String entityId) {
        return managementContextService.executionManager.getTasksWithTag(getEntity(entityId)).collect { new TaskSummary(it) }
    }

    public Collection<SensorSummary> getSensorsOfEntity(String entityId) {
        Set<SensorSummary> results = []
        Entity entity = getEntity(entityId)
        if (entity) {
            for (Sensor s: entity.entityClass.sensors) {
                results.add(new SensorSummary(s, entity.getAttribute(s)))
            }
        }
        return results
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
        Set<Entity> result = []

        if (parent.properties.containsKey("ownedChildren")) {
            parent.ownedChildren.each { result << it }
        }

        result
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
}
