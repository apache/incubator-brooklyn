package brooklyn.web.console

import grails.converters.JSON
import grails.plugins.springsecurity.Secured

import brooklyn.entity.Entity
import brooklyn.web.console.entity.EntitySummary
import brooklyn.web.console.entity.LocationSummary
import brooklyn.location.Location
import brooklyn.location.basic.AbstractLocation

import brooklyn.web.console.entity.JsTreeNode
import brooklyn.web.console.EntityService.NoSuchEntity
import brooklyn.entity.Effector

@Secured(['ROLE_ADMIN'])
class EntityController {

    // Injected
    def entityService

    def index = {
        redirect(uri:"/dashboard/")
    }

    def circles = {
        Map<Location, Integer> ls = entityService.entityCountsAtLocatedLocations()
        def forJSON = ls.collect { l, count -> [ lat: l.getLocationProperty("latitude"),
                                                 lng: l.getLocationProperty("longitude"),
                                                 entity_count: count ] }
        render(forJSON as JSON)
    }

    def list = {
        render(toEntitySummaries(entityService.getAllEntities()) as JSON)
    }

    def info = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                render(toEntitySummary(entity) as JSON)
            } else {
                render(status: 404,
                       text: '{message: "Cannot retrieve info: Entity with specified id '+ params.id + ' does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }

    def search = {
        render(toEntitySummaries(entityService.getEntitiesMatchingCriteria(params.name, params.id, params.applicationId))
               as JSON)
    }

    def breadcrumbs = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                List<Entity> parents = entityService.getAncestorsOf(entity);
                parents.reverse()
                String childName = entity.displayName
                def result = []
                result += childName
                for(p in parents){
                    Entity parent = p
                    result += parent.displayName
                }
                render(result as JSON)
            } else {
                render(status: 404,
                       text: '{message: "Cannot retrieve info: Entity with specified id '+ params.id + ' does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }

    def locations = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                List<AbstractLocation> entityLocations = entity.getLocations()
                def locationSummaries = []
                for (loc in entityLocations){
                    //for each loc create location summary and push to array
                    locationSummaries += toLocationSummary(loc)
                }
                render(locationSummaries as JSON)
            }
             else {
                render(status: 404,
                       text: '{message: "Cannot retrieve info: Entity with specified id '+ params.id + ' does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }

    def effectors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
        render entityService.getEffectorsOfEntity(params.id) as JSON
    }

    def sensors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }

        try {
            render entityService.getSensorData(params.id) as JSON
        } catch (NoSuchEntity e) {
            render(status: 404, text: '{message: "Entity with specified id does not exist"}')
        }
    }

    def activity = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return
        }
        render entityService.getTasksOfEntity(params.id) as JSON
    }

    def allActivity = {
        render entityService.getTasksOfAllEntities() as JSON
    }

    def jstree = {
        Map<String, JsTreeNode> nodeMap = [:]
        Collection<Entity> entities = entityService.getAllEntities()

        entities.each { nodeMap.put(it.id, new JsTreeNode(it, true)) }

        entities.each {
            entity ->
                entity.getOwnedChildren().each {
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
    
    private LocationSummary toLocationSummary(Location location){
        return new LocationSummary(location);
    }
}
