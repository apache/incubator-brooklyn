package brooklyn.web.console

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionManager
import brooklyn.web.console.entity.TestEffector
import brooklyn.entity.webapp.tomcat.TomcatNode

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
        TestApplication() {
            this.id = "app-" + ManagementContextService.ID_GENERATOR++
            displayName = "Application";

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


            sensors.putAll([
                    Children: new BasicAttributeSensor<Integer>(Integer.class, "Children", "Owned children of this application"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])

            setAttribute(getSensor("Children"), getOwnedChildren().size())

        }

        AbstractGroup addOwnedChildren(Collection<Entity> children) {
            children.each { addOwnedChild(it) }
            return this
        }

        private class TestGroupEntity extends AbstractGroup {
            TestGroupEntity(String displayName) {
                this.id = "group-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName
                sensors.putAll([
                        Children: new BasicAttributeSensor<Integer>(Integer.class, "Children", "Direct children of this group"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
            }

            TestGroupEntity addOwnedChildren(Collection<Entity> children) {
                children.each { addOwnedChild(it) }
                setAttribute(getSensor("Children"), children.size())
                return this
            }
        }

        private class TestDataEntity extends AbstractEntity {
            TestDataEntity(String displayName) {
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName;

                TestEffector startDB = new TestEffector("Start DB", "This will start the database",  new ArrayList<ParameterType<?>>())
                TestEffector stopDB = new TestEffector("Stop DB", "This will stop the database", new ArrayList<ParameterType<?>>())
                TestEffector restartDB = new TestEffector("Restart DB", "This will restart the DB", new ArrayList<ParameterType<?>>())

                this.effectors.putAll(["Start DB": startDB, "Stop DB": stopDB, "Restart DB": restartDB])

                this.sensors.putAll(
                       [Happiness: new BasicAttributeSensor<String>(String.class, "Happiness"),
                        Cache: new BasicAttributeSensor<String>(String.class, "Cache", "Some cache metric"),
                        Sync: new BasicAttributeSensor<String>(String.class, "Sync", "Synchronization strategy")]
                )

                setAttribute(getSensor("Happiness"), 50)
                setAttribute(getSensor("Cache"), 200)
                setAttribute(getSensor("Sync"), "Moop")
            }
        }

        private class TestTomcatEntity extends AbstractEntity {
            private Map hackMeIn = [
                    "http.port": 8080,
                    "webapp.tomcat.shutdownPort": 666,
                    "jmx.port": 1000,
                    "webapp.reqs.processing.time": 100
            ]

            public TestTomcatEntity(String displayName) {
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++
                this.displayName = displayName;
                this.id = id;

                // Stealing the sensors from TomcatNode
                this.sensors.putAll(new TomcatNode().sensors)

                // Don't appear to be any effectors in TomcatNode
                TestEffector startTomcat = new TestEffector("Start Tomcat", "This will start Tomcat at a specified location",  new ArrayList<ParameterType<?>>())
                TestEffector stopTomcat = new TestEffector("Stop Tomcat", "This will stop tomcat at its current location", new ArrayList<ParameterType<?>>())
                TestEffector restartTomcat = new TestEffector("Restart Tomcat", "This will restart tomcat in its current location", new ArrayList<ParameterType<?>>())

                this.effectors.putAll([  "Start Tomcat": startTomcat,
                                    "Stop Tomcat": stopTomcat,
                                    "Restart Tomcat": restartTomcat])

                // TODO should we be looking in entityClass (rather than calling getSensor?)
                for (String key: hackMeIn.keySet()) {
                    this.setAttribute(getSensor(key), hackMeIn[key] + ManagementContextService.ID_GENERATOR)
                }
            }
        }
    }
}