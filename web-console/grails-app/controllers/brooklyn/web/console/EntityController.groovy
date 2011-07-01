package brooklyn.web.console

import grails.converters.JSON
import grails.plugins.springsecurity.Secured

import brooklyn.entity.Entity
import brooklyn.web.console.entity.EntitySummary
import brooklyn.web.console.entity.JsTreeNodeImpl

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

    def sensors = {
        String id = "leaf-\\d*"
        Collection<Entity> entities = entityService.getEntitiesMatchingCriteria(null, id, null)
        if (entities.size() == 0) {
            // log maybe
            render(status: 404, text: '{message: "Entity with specified id does not exist"}')
            return
        }

        Entity entity = entities.toArray()[0]

        render entity.sensorReadings as JSON
    }

    def jstree = {
        Map<String, JsTreeNodeImpl> nodeMap = [:]
        Collection<Entity> entities = entityService.getAllEntities()
        JsTreeNodeImpl root = new JsTreeNodeImpl("root", ".", "root", true)

        entities.each { nodeMap.put(it.id, new JsTreeNodeImpl(it, true)) }

        entities.each {
            entity ->
            entityService.getChildren(entity).each {
                child -> nodeMap[entity.id].children.add(nodeMap[child.id])
            }
        }

        // TODO Place matches at the root of our tree view (iff an ancestor isn't already present)
        Collection<Entity> matches = entityService.getEntitiesMatchingCriteria(params.name, params.id, params.applicationId);
        matches.each { match ->
            if (!entityService.isChildOf(match, matches)) {
                root.children.add(nodeMap[match.id])
            }
        }

        render(root as JSON)
    }

    private Set<EntitySummary> toEntitySummaries(Collection<Entity> entities) {
        entities.collect {  new EntitySummary(it) }
    }
}
