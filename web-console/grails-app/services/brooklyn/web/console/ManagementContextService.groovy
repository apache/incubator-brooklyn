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
import brooklyn.util.task.BasicExecutionManager
import brooklyn.management.Task

class ManagementContextService implements ManagementContext {
    private final ExecutionManager executionManager = new BasicExecutionManager();
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
        return executionManager;
    }

    public SubscriptionManager getSubscriptionManager() {
        throw new UnsupportedOperationException();
    }

    private class TestApplication extends AbstractApplication {
        TestApplication() {
            this.id = "app-" + ManagementContextService.ID_GENERATOR++
            displayName = "Application";

            for(String tierName : ["tomcat tier 1", "tomcat tier 2", "data tier 1"]) {
                Entity tier = new TestGroupEntity(this, tierName);
                for(String clusterName : ["1a", "1b"]) {
                    Entity cluster = new TestGroupEntity(tier, tierName.substring(0, tierName.indexOf(" ")) + " cluster " + clusterName)
                    for(int i=1; i<4; i++) {
                        if (tierName =~ /^tomcat/) {
                            cluster.addOwnedChild(new TestTomcatEntity(cluster, "tomcat node " + clusterName + "." + i))
                        } else {
                            cluster.addOwnedChild(new TestDataEntity(cluster, "data node " + clusterName + "." + i))
                        }

                    }
                    tier.addOwnedChild(cluster)
                }
                addOwnedChild(tier)
            }

            sensors.putAll([
                    Children: new BasicAttributeSensor<Integer>(Integer.class, "Children", "Owned children of this application"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
            setAttribute(getSensor("Children"), getOwnedChildren().size())
        }

        private class TestGroupEntity extends AbstractGroup {
            TestGroupEntity(Entity owner, String displayName) {
                super([:], owner)
                this.displayName = displayName
                this.id = "group-" + ManagementContextService.ID_GENERATOR++
                sensors.putAll([Children: new BasicAttributeSensor<Integer>(Integer.class, "Children", "Direct children of this group"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
            }

            TestGroupEntity addOwnedChild(Entity child) {
                super.addOwnedChild(child)
                setAttribute(getSensor("Children"), ownedChildren.size())
                return this
            }
        }

        private class TestDataEntity extends AbstractEntity {
            TestDataEntity(Entity owner, String displayName) {
                super([:], owner)

                this.displayName = displayName
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++

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

            public TestTomcatEntity(Entity owner, String displayName) {
                super([:], owner)
                this.displayName = displayName
                this.id = "leaf-" + ManagementContextService.ID_GENERATOR++

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

                this.getExecutionContext().submit([displayName: "myTask", description: "some task or other"], new Runnable() {
                    void run() {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                })


            }
        }
    }
}