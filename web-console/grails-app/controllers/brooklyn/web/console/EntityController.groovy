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

    private Collection<Entity> getTheKiddies(Entity parent) {
        Set<Entity> result = []
        parent.ownedChildren.each { result << it }
        parent.members.each { result << it }
        result
    }

    private boolean isChildOf(Entity child, Collection<Entity> parents) {
        parents.find { parent ->
           getTheKiddies(parent).contains(child) || isChildOf(getTheKiddies(parent), child)
        }
    }

    private ManagementContext context = new TestManagementContext();

    private Collection<Entity> getTopLevelEntities() {
        return context.applications
    }

    private Collection<Entity> getAllEntities() {
        return flattenEntities(getTopLevelEntities());
    }

    private Set<Entity> flattenEntities(Collection<Entity> entities) {
        Set<Entity> dest = []
        entities.each {
            e ->
            dest.add(e)
            getTheKiddies(e).each {
                dest.addAll(flattenEntities([it]))
            }
        }
        dest
    }

    private Set<Entity> getEntitiesMatchingCriteria(String name, String id, String applicationId) {
        getAllEntities().findAll {
            it ->
            ((!name || it.displayName =~ name)
                    && (!id || it.id =~ id)
                    && (!applicationId || it.application.id =~ applicationId)
            )
        }
    }

    private Set<EntitySummary> toEntitySummaries(Collection<Entity> entities) {
        entities.collect {  new EntitySummary(it) }
    }

    def index = {}

    def list = {
        render(toEntitySummaries(getAllEntities()) as JSON)
    }

    def search = {
        render(toEntitySummaries(getEntitiesMatchingCriteria(params.name, params.id, params.applicationId)) as JSON)
    }

    def jstree = {
        Map<String, JsTreeNodeImpl> nodeMap = [:]
        Collection<Entity> all = getAllEntities()
        JsTreeNodeImpl root = new JsTreeNodeImpl("root", ".", "root", true)

        all.each { nodeMap.put(it.id, new JsTreeNodeImpl(it, true)) }

        all.each {
            entity ->
            getTheKiddies(entity).each {
                child -> nodeMap[entity.id].children.add(nodeMap[child.id])
            }
        }

        // TODO Place matches at the root of our tree view (iff an ancestor isn't already present)
//        Collection<Entity> matches = getEntitiesMatchingCriteria(params.name, params.id, params.applicationId);
//        matches.each { match ->
//            if (!isChildOf(match, matches)) {
//                root.children.add(nodeMap[match.id])
//            }
//        }

        getTopLevelEntities().each {
            root.children.add(nodeMap[it.id])
        }

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

        addOwnedChildren([
                new TestGroupEntity("tomcat tier 1").addOwnedChildren([
                        new TestGroupEntity("tomcat cluster 1a").addOwnedChildren([
                                new TestLeafEntity("tomcat node 1a.1"),
                                new TestLeafEntity("tomcat node 1a.2"),
                                new TestLeafEntity("tomcat node 1a.3"),
                                new TestLeafEntity("tomcat node 1a.4")]),
                        new TestGroupEntity("tomcat cluster 1b").addOwnedChildren([
                                new TestLeafEntity("tomcat node 1b.1"),
                                new TestLeafEntity("tomcat node 1b.2"),
                                new TestLeafEntity("tomcat node 1b.3"),
                                new TestLeafEntity("tomcat node 1b.4")])
                ]),
                new TestGroupEntity("data tier 1").addOwnedChildren([
                        new TestGroupEntity("data cluster 1a").addOwnedChildren([
                                new TestLeafEntity("data node 1a.1"),
                                new TestLeafEntity("data node 1a.2"),
                                new TestLeafEntity("data node 1a.3")])
                ])
        ])
    }

    AbstractGroup addOwnedChildren(Collection<Entity> children) {
        children.each { addOwnedChild(it) }
        return this
    }
}

private class TestGroupEntity extends AbstractGroup {
    TestGroupEntity(String displayName) {
        this.displayName = displayName
    }

    TestGroupEntity addOwnedChildren(Collection<Entity> children) {
        children.each { addOwnedChild(it) }
        return this
    }
}

private class TestLeafEntity extends AbstractEntity {
    TestLeafEntity(String displayName) {
        this.displayName = displayName;
    }
}
