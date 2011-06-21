package brooklyn.web.console

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Application;
import brooklyn.entity.EntityClass;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.Event;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import grails.converters.JSON

import java.util.Collection
import java.util.Map

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.ExecutionManager

import grails.plugins.springsecurity.Secured

@Secured(['ROLE_ADMIN'])
class EntityController {

    private ManagementContext context = new TestManagementContext();
    
    private Collection<Entity> getEntitiesMatchingCriteria(Collection<Entity> all, String name, String id, String applicationId) {
        Collection<Entity> matches = all.findAll {
            it ->
            ((!name || it.displayName =~ name)
                    && (!id || it.id =~ id)
                    && (!applicationId || it.applicationId =~ applicationId)
            )
        }

        for (Entity match: matches) {
            all.findAll {
                s -> s.groupIds.contains(match.id)
            }
        }

        return matches
    }

    private Collection<EntitySummary> toEntitySummaries(Collection<Entity> entities) {
        return entities.collect { new EntitySummary(it) }
    }
    
    // TODO Enhancement for future user-story: multiple applications?
    private Collection<Entity> getAllEntities() {
        return context.applications[0].entities
    }


    def index = {}

    def list = {
        render(toEntitySummaries(getAllEntities) as JSON)
    }

    def search = {
        def result = getEntitiesMatchingCriteria(allEntities, params.name, params.id, params.applicationId)
        render(toEntitySummaries(result) as JSON)
    }

    def jstree = {
        List<Entity> all = allEntities
        Map<String, JsTreeNodeImpl> nodeMap = [:]
        JsTreeNodeImpl root = new JsTreeNodeImpl("root", ".", true)

        for (Entity entity: all) {
            nodeMap.put(entity.id, new JsTreeNodeImpl(entity.id, entity.displayName))
        }

        all.each { entity ->
            entity.children.each { child ->
                entity.add( nodeMap.get(child.id) )
            }
        }

        List<JsTreeNodeImpl> potentialRoots = []
        for (Entity entity : getEntitiesMatchingCriteria(all, params.name, params.id, params.applicationId)) {
            nodeMap[entity.id].matched = true
            potentialRoots.add(nodeMap[summary.id])
        }

        root.children.addAll(potentialRoots.findAll { a -> !potentialRoots.findAll { n -> n.hasDescendant(a)} })

        render(root as JSON)
    }
}

private class TestManagementContext implements ManagementContext {
    private final Application app = new TestApplication();

    Collection<Application> getApplications() {
        return Collections.singleton(app);
    }

    Entity getEntity(String id) {
        throw new UnsupportedOperationException();
    }
    
    public ExecutionManager getExecutionManager() {
        throw new UnsupportedOperationException();
    }
}

private class TestApplication extends AbstractApplication {
    TestApplication() {
        displayName = "Application";
        
        addOwnedChildren(
                new TestGroupEntity("tomcat tier 1").addOwnedChildren(
                        new TestGroupEntity("tomcat cluster 1a").addOwnedChildren(
                                new TestLeafEntity("tomcat node 1a.1"),
                                new TestLeafEntity("tomcat node 1a.2"),
                                new TestLeafEntity("tomcat node 1a.3"),
                                new TestLeafEntity("tomcat node 1a.4") ),
                        new TestGroupEntity("tomcat cluster 1b").addOwnedChildren(
                            new TestLeafEntity("tomcat node 1b.1"),
                            new TestLeafEntity("tomcat node 1b.2"),
                            new TestLeafEntity("tomcat node 1b.3"),
                            new TestLeafEntity("tomcat node 1b.4") )
                         ),
                new TestGroupEntity("data tier 1").addOwnedChildren(
                    new TestGroupEntity("data cluster 1a").addOwnedChildren(
                            new TestLeafEntity("data node 1a.1"),
                            new TestLeafEntity("data node 1a.2"),
                            new TestLeafEntity("data node 1a.3") )
                     )
                )
    }
    
    TestApplication addOwnedChildren(Entity... children) {
        children.each { addOwnedChild(it) }
        return this
    }
}

private class TestGroupEntity extends AbstractGroup {
    TestGroupEntity(String displayName) {
        this.displayName = displayName
    }
    
    TestGroupEntity addOwnedChildren(Entity... children) {
        children.each { addOwnedChild(it) }
        return this
    }
}

private class TestLeafEntity extends AbstractEntity {
    TestLeafEntity(String displayName) {
        this.displayName = displayName;
    }
}
