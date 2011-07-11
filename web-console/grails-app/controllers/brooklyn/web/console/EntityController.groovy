package brooklyn.web.console

import grails.converters.JSON
import grails.plugins.springsecurity.Secured

import brooklyn.entity.Entity
import brooklyn.web.console.entity.EntitySummary

import brooklyn.web.console.entity.JsTreeNode
import brooklyn.management.Task
import brooklyn.web.console.entity.SensorSummary
import brooklyn.web.console.entity.TaskSummary

@Secured(['ROLE_ADMIN'])
class EntityController {

    // Injected
    def entityService

    def index = {}

    def list = {
        render(toEntitySummaries(entityService.getAllEntities()) as JSON)
    }

    def search = {
        render(toEntitySummaries(entityService.getEntitiesMatchingCriteria(params.name, params.id, params.applicationId)) as JSON)
    }

    def effectors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return
        }
        render entityService.getEffectorsOfEntity(params.id) as JSON
    }

    def sensors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return
        }
        render entityService.getSensorsOfEntity(params.id) as JSON
    }

    def activity = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return
        }
        render entityService.getTasksOfEntity(params.id) as JSON
    }


    def jstree = {
        Map<String, JsTreeNode> nodeMap = [:]
        Collection<Entity> entities = entityService.getAllEntities()

        entities.each { nodeMap.put(it.id, new JsTreeNode(it, true)) }

        entities.each {
            entity ->
            entityService.getChildren(entity).each {
                child -> nodeMap[entity.id].children.add(nodeMap[child.id])
            }
        }

        List<JsTreeNode> roots = []
        Collection<Entity> matches = entityService.getEntitiesMatchingCriteria(params.name, params.id, params.applicationId);
        matches.each { match ->
            if (!entityService.isChildOf(match, matches)) {
                roots.add(nodeMap[match.id])
            }
        }

        render([roots] as JSON)
    }

    private EntitySummary toEntitySummary(Entity entity) {
        return new EntitySummary(entity);
    }

    private Set<EntitySummary> toEntitySummaries(Collection<Entity> entities) {
        entities.collect { toEntitySummary(it) }
    }


    private TaskSummary toTaskSummary(Task task) {
        return new TaskSummary(task)
    }

    private Set<EntitySummary> toTaskSummaries(Collection<Task> tasks) {
        tasks.collect { toTaskSummary(it) }
    }

    private Entity getEntityMatchingId(String id) {
        Set<Entity> entities = entityService.getEntitiesMatchingCriteria(null, id, null)

        if (!id) {
            return null;
        }

        if (entities.size() == 0) {
            return null;
        }

        return entities.toArray()[0]
    }
}
