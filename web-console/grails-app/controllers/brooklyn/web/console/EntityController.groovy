package brooklyn.web.console

import grails.converters.JSON
import grails.plugins.springsecurity.Secured

import brooklyn.entity.Entity
import brooklyn.web.console.entity.EntitySummary

import brooklyn.web.console.entity.JsTreeNode

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
        render getEntityMatchingId(params.id).effectors as JSON
    }

    def sensors = {
       render getEntityMatchingId(params.id).sensors as JSON
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

    private Set<EntitySummary> toEntitySummaries(Collection<Entity> entities) {
        entities.collect {  new EntitySummary(it) }
    }

    private EntitySummary getEntityMatchingId(String id){
        Set<EntitySummary> entities = toEntitySummaries(entityService.getEntitiesMatchingCriteria(null, id, null))

        if (! id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return null;
        }

        if (entities.size() == 0) {
            render(status: 404, text: '{message: "Entity with specified id does not exist"}')
            return null;
        }

        return entities.toArray()[0]
    }
}
