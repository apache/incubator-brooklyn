package brooklyn.web.console

import brooklyn.management.ManagementContext
import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionManager
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractEntity

class ManagementContextService implements ManagementContext {
    private final Application application = new TestApplication();
    static transactional = false;
    protected static int ID_GENERATOR = 0;

    Collection<Application> getApplications() {
        return Collections.singletonList(application);
    }

    Entity getEntity(String id) {
        throw new UnsupportedOperationException();
    }

    public ExecutionManager getExecutionManager() {
        throw new UnsupportedOperationException();
    }

    public SubscriptionManager getSubscriptionManager() {
        throw new UnsupportedOperationException();
    }

    private class TestApplication extends AbstractApplication {
        Map sensorReadings = [:];

        TestApplication() {
            this.id = "app-" + ManagementContextService.ID_GENERATOR++
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

        private class TestGroupEntity extends AbstractGroup {
            Map sensorReadings = [:];
            TestGroupEntity(String displayName) {
                this.id = "group-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName
            }

            TestGroupEntity addOwnedChildren(Collection<Entity> children) {
                children.each { addOwnedChild(it) }
                return this
            }
        }

        private class TestLeafEntity extends AbstractEntity {
            /* Temporarily using fake sensor data provider.
             * Should switch to using the sensors field of AbstractEntity:
                 private Map<String,Sensor> sensors = null
            */
            Map sensorReadings;

            TestLeafEntity(String displayName) {
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName;
                this.sensorReadings = [ Happiness: 42,
                                        WubWub: 16,
                                        Pingu: "Moop",
                                        id: this.id]

            }
        }
    }
}
