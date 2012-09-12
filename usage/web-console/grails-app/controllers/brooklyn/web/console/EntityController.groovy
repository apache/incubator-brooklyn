package brooklyn.web.console

import grails.converters.JSON

import brooklyn.entity.Entity
import brooklyn.web.console.entity.EntitySummary
import brooklyn.web.console.entity.LocationSummary
import brooklyn.web.console.entity.PolicySummary
import brooklyn.location.Location
import brooklyn.location.basic.AbstractLocation
import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.policy.basic.GeneralPurposePolicy

import brooklyn.web.console.entity.JsTreeNode
import brooklyn.web.console.EntityService.NoSuchEntity
import brooklyn.entity.Effector
import brooklyn.entity.Group;
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.BasicParameterType


class EntityController {

    // Injected
    def entityService

    def index = {
        redirect(uri:"/dashboard/")
    }

    def circles = {
        Map<Location, Integer> ls = entityService.entityCountsAtLocatedLocations()

        // Add locations that have no entities
        entityService.applicationLocations().each {
            if(!ls[it]) {
                ls[it] = 0;
            }
        }

        def locationInfo = [:];
        ls.each { l, count -> locationInfo[l.getId()] = [
                                lat: l.getLocationProperty("latitude"),
                                lng: l.getLocationProperty("longitude"),
                                entity_count: count ]
        }

        render(locationInfo as JSON)
    }

    def list = {
        render(entityService.getAllEntities().collect {new EntitySummary(it)} as JSON)
    }

    def info = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                render(new EntitySummary(entity) as JSON)
            } else {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }

    def search = {
        render(entityService.getEntitiesMatchingCriteria(params.name, params.id, params.applicationId)
               .collect {new EntitySummary (it)} as JSON)
    }

    def breadcrumbs = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                List<Entity> parents = entityService.getAncestorsOf(entity)
                parents.reverse()
                def result = [[entity.id, entity.displayName]]
                for(parent in parents){
                    result.add([parent.id, parent.displayName])
                }
                render(result as JSON)
            } else {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
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
                Collection<AbstractLocation> entityLocations = entity.getLocations()
                def locationSummaries = []
                for (loc in entityLocations){
                    //for each loc create location summary and push to array
                    locationSummaries += new LocationSummary(loc)
                }
                render(locationSummaries as JSON)
            }
             else {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }

    def policies = {
        String id = params.id
        if (id) {
            Entity entity = entityService.getEntity(id)
            if (entity != null) {
                Collection<AbstractPolicy> entityPolicies = entityService.getPoliciesOfEntity(entity)
                def policySummaries = []
                for (policy in entityPolicies){
                    //for each policy create policy summary and push to array
                    policySummaries += new PolicySummary(policy)
                }
                render(policySummaries as JSON)
            }
             else {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
            }
        } else {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
    }


    def effectors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }
        
        try {
            render entityService.getEffectorsOfEntity(params.id) as JSON
        } catch (NoSuchEntity e) {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
        }
    }

    def sensors = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
        }

        try {
            render entityService.getSensorData(params.id) as JSON
        } catch (NoSuchEntity e) {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+'does not exist"}')
        }
    }
    def sensor = {
        if (!params.entityId) {
            render(status: 400, text: '{message: "You must provide an entityId"}')
        }
        if (!params.sensorId) {
            render(status: 400, text: '{message: "You must provide a sensorId"}')
        }

        try {
            Entity ent = entityService.getEntity(params.entityId);
            def v = ent.getAttribute(ent.getEntityType().getSensor(params.sensorId));
            try { render(v as JSON) }
            catch (Exception e) {
                //not json, just return as text
                render(text: ""+v)
            }
        } catch (NoSuchEntity e) {
            render(status: 404, text: '{message: "Entity with specified id '+params.entityId+'does not exist"}')
        }
    }

    def activity = {
        if (!params.id) {
            render(status: 400, text: '{message: "You must provide an entity id"}')
            return
        }
        
        try {
            render entityService.getTasksOfEntity(params.id) as JSON
        } catch (NoSuchEntity e) {
            render(status: 404, text: '{message: "Entity with specified id '+params.id+' does not exist"}')
        }
    }

    def allActivity = {
        render entityService.getTasksOfAllEntities() as JSON
    }

    def jstree = {
        Map<String, JsTreeNode> nodeMap = [:]
        Collection<Entity> entities = entityService.getAllEntities()

        entities.each { nodeMap.put(it.id, new JsTreeNode(it)) }

        entities.each {
            entity ->
                boolean hasChildren = false;
                entity.getOwnedChildren().each {
                    child -> 
                    hasChildren = true;
                    nodeMap[entity.id].children.add(nodeMap[child.id])
                }
                if (!hasChildren && entity in Group) ((Group)entity).getMembers().each {
                    //if we have a group with no children (the usual case for groups) show its members instead
                    //NB: tree view seems to hang if multiple identical children (poss mutiple identical elements in tree?) 
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


    def invoke = {
        Entity entity = entityService.getEntity(params.entityId)
        if (!entity) {
            render(status: 404, text: '{message: "Entity with specified id '+params.entityId+'does not exist"}')
            return
        }
        
        Collection<Effector> effectorsOfEntity = entityService.getEffectorsOfEntity(entity.id)

        if(effectorsOfEntity != null){
            Effector effector = effectorsOfEntity.find {
                it.name.equals(params.effectorName)
            }

            if(effector != null){
                Map<String,?> parameters = new HashMap<String,?>()
                effector.parameters.each {
                    if (params.get(it.name)) {
                        String parameterName = it.name
                        Class parameterClass = it.parameterClass
                        parameters.put(parameterName,
                                Collection.isAssignableFrom(parameterClass) ?
                                params.get(parameterName).split('\n') :
                                parameterClass.newInstance(params.get(parameterName)))
                    }
                }
                entity.invoke(effector, parameters)
            } else {
                render(status: 404, text: '{message: "Cannot invoke effector '+ params.effectorName + ' does not exist"}')
            }
        }
        render true
    }
    /* Execute an action against a policy with given ID and action string*/
    def policyaction = {
        Entity entity = entityService.getEntity(params.entityId)
        if (!entity) {
            render(status: 404, text: '{message: "Entity with specified id '+params.entityId+'does not exist"}')
            return
        }
        Policy policy = entityService.getPolicyOfEntity(entity, params.policyId)
        if(policy != null){
            String action = params.chosenAction
            entityService.executePolicyAction(action, policy, entity)
        } else {
            render(status: 404, text: '{message: "Cannot invoke policy action for '+ params.policyId + ' does not exist"}')
        }
        render true
    }

}
