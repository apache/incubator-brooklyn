package brooklyn.web.console

import brooklyn.entity.Entity

class EntityService {

    static transactional = false
    def managementContextService

    public Collection<Entity> getTheKiddies(Entity parent) {
        Set<Entity> result = []

        if (parent.properties.containsKey("ownedChildren")){
            parent.ownedChildren.each { result << it }
        }
        if(parent.properties.containsKey("members")){
            parent.members.each { result << it }
        }

        result
    }

    public boolean isChildOf(Entity child, Collection<Entity> parents) {
        parents.find { parent ->
           getTheKiddies(parent).contains(child) || isChildOf(getTheKiddies(parent), child)
        }
    }

    public Collection<Entity> getTopLevelEntities() {
        return managementContextService.applications
    }

    public Collection<Entity> getAllEntities() {
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

    public Set<Entity> getEntitiesMatchingCriteria(String name, String id, String applicationId) {
        getAllEntities().findAll {
            it ->
            ((!name || it.displayName =~ name)
                    && (!id || it.id =~ id)
                    && (!applicationId || it.application.id =~ applicationId)
            )
        }
    }
}
