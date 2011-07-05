package brooklyn.web.console

import brooklyn.management.ManagementContext
import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionManager
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractEntity
import brooklyn.web.console.entity.TestEffector
import brooklyn.entity.ParameterType

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
        Map sensorReadings;
        TestApplication() {
            this.id = "app-" + ManagementContextService.ID_GENERATOR++
            displayName = "Application";

            this.sensorReadings = [ id: this.id,
                                    "Application Entity": true ];

            addOwnedChildren([
                    new TestGroupEntity("tomcat tier 1").addOwnedChildren([
                            new TestGroupEntity("tomcat cluster 1a").addOwnedChildren([
                                    new TestTomcatEntity("tomcat node 1a.1"),
                                    new TestTomcatEntity("tomcat node 1a.2"),
                                    new TestTomcatEntity("tomcat node 1a.3"),
                                    new TestTomcatEntity("tomcat node 1a.4")]),
                            new TestGroupEntity("tomcat cluster 1b").addOwnedChildren([
                                    new TestTomcatEntity("tomcat node 1b.1"),
                                    new TestTomcatEntity("tomcat node 1b.2"),
                                    new TestTomcatEntity("tomcat node 1b.3"),
                                    new TestTomcatEntity("tomcat node 1b.4")])
                    ]),
                    new TestGroupEntity("data tier 1").addOwnedChildren([
                            new TestGroupEntity("data cluster 1a").addOwnedChildren([
                                    new TestDataEntity("data node 1a.1"),
                                    new TestDataEntity("data node 1a.2"),
                                    new TestDataEntity("data node 1a.3")])
                    ])
            ])
        }

        AbstractGroup addOwnedChildren(Collection<Entity> children) {
            children.each { addOwnedChild(it) }
            return this
        }

        private class TestGroupEntity extends AbstractGroup {
            Map sensorReadings;
            TestGroupEntity(String displayName) {
                this.id = "group-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName
                this.sensorReadings = [ id: this.id,
                                        "Group entity": true ];
            }

            TestGroupEntity addOwnedChildren(Collection<Entity> children) {
                children.each { addOwnedChild(it) }
                return this
            }
        }

        private class TestDataEntity extends AbstractEntity {
            /* Temporarily using fake sensor data provider.
             * Should switch to using the sensors field of AbstractEntity:
                 private Map<String,Sensor> sensors = null
            */
            Map sensorReadings

            TestDataEntity(String displayName) {
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName;
                this.sensorReadings = [ Happiness: 50,
                                        Cache: 200,
                                        Sync: "Moop",
                                        id: this.id]
            }
        }

        private class TestTomcatEntity extends AbstractEntity {
            Map sensorReadings
            Map effectors

            TestEffector startTomcat = new TestEffector("Start Tomcat", "This will start Tomcat at a specified location",  new ArrayList<ParameterType<?>>())
            TestEffector stopTomcat = new TestEffector("Stop Tomcat", "This will stop tomcat at its current location", new ArrayList<ParameterType<?>>())
            TestEffector restartTomcat = new TestEffector("Restart Tomcat", "This will restart tomcat in its current location", new ArrayList<ParameterType<?>>())

            TestTomcatEntity(String displayName) {
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName;
                this.sensorReadings = [ Happiness: 42,
                                        Errors: 16,
                                        Status: "Started",
                                        id: this.id]

                this.effectors = [  "Start Tomcat": startTomcat,
                                    "Stop Tomcat": stopTomcat,
                                    "Restart Tomcat": restartTomcat]
            }
        }
    }
}