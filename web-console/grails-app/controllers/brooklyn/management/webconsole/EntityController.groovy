package brooklyn.management.webconsole

import grails.converters.JSON

import brooklyn.entity.basic.BasicEntitySummary
import brooklyn.entity.Entity
import brooklyn.entity.EntitySummary
import brooklyn.management.ManagementContext

class EntityController {

    class TestManagementApi implements ManagementContext {
        Collection<EntitySummary> getApplicationSummaries() {
            return new ArrayList<EntitySummary>();
        }

        Collection<EntitySummary> getEntitySummariesInApplication(String id) {
            return new ArrayList<EntitySummary>();
        }

        Collection<EntitySummary> getAllEntitySummaries() {
            return [
                    getHackyEntitySummary("a1", "Application", []),
                    getHackyEntitySummary("t1", "tomcat tier 1", ["a1"]),
                    getHackyEntitySummary("tcc1a", "tomcat cluster 1a", ["t1"]),
                    getHackyEntitySummary("tc1a1", "tomcat node 1a.1", ["tcc1a"]),
                    getHackyEntitySummary("tc1a2", "tomcat node 1a.2", ["tcc1a"]),
                    getHackyEntitySummary("tc1a3", "tomcat node 1a.3", ["tcc1a"]),
                    getHackyEntitySummary("tc1a4", "tomcat node 1a.4", ["tcc1a"]),
                    getHackyEntitySummary("tcc1b", "tomcat cluster 1b", ["t1"]),
                    getHackyEntitySummary("tc1b1", "tomcat node 1b.1", ["tcc1b"]),
                    getHackyEntitySummary("tc1b2", "tomcat node 1b.2", ["tcc1b"]),
                    getHackyEntitySummary("tc1b3", "tomcat node 1b.3", ["tcc1b"]),
                    getHackyEntitySummary("tc1b4", "tomcat node 1b.4", ["tcc1b"]),
                    getHackyEntitySummary("d1", "data tier 1", ["a1"]),
                    getHackyEntitySummary("dbc1", "data cluster 1a", ["d1"]),
                    getHackyEntitySummary("db1", "data node 1a.1", ["dbc1"]),
                    getHackyEntitySummary("db2", "data node 1a.2", ["dbc1"]),
                    getHackyEntitySummary("db3", "data node 1a.3", ["dbc1"]),
            ];
        }

        Entity getEntity(String id) {
            null;
        }

        EntitySummary getHackyEntitySummary(String id, displayName, ArrayList<String> groups) {
            return new BasicEntitySummary(id, displayName, "app1", groups);
        }
    }

    ManagementContext context = new TestManagementApi();

    def index = {}

    def list = {
        render context.getAllEntitySummaries() as JSON;
    }

    def jstree = {
        JsTreeNodeImpl root = new JsTreeNodeImpl("root", "FAILED TO DETERMINE ROOT OF TREE", [])
        Map<String, JsTreeNodeImpl> nodeMap = [:]
        for (EntitySummary summary: context.getAllEntitySummaries()) {
            nodeMap.put(summary.getId(), new JsTreeNodeImpl(summary.getId(), summary.getDisplayName(), []))
        }

        for (EntitySummary summary: context.getAllEntitySummaries()) {
            JsTreeNode node = nodeMap.get(summary.getId())
            if (summary.getGroupIds().isEmpty()) {
                root = node;
            }
            for (String groupId: summary.getGroupIds()) {
                nodeMap.get(groupId).getChildren().add(node)
            }
        }
        render root as JSON
    }

}

