package brooklyn.web.console

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.webapp.tomcat.TomcatNode
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.ExecutionManager
import brooklyn.management.ManagementContext
import brooklyn.management.internal.LocalManagementContext
import brooklyn.web.console.entity.TestEffector

class ManagementContextService {
    
    //FIXME: how should this be supplied?  (previously it called to LocalManagementContext to generate, but that isn't the answer...)  --Alex
    //it does have to get set in the application
    private final ManagementContext context = new LocalManagementContext()
    private final Application application = new TestApplication()
    
    protected static int ID_GENERATOR = 0

    public ManagementContextService() {
        context.applications.add(application)
    }

    Collection<Application> getApplications() {
        return context.applications
    }

    Entity getEntity(String id) {
        return context.getEntity(id)
    }

    public ExecutionManager getExecutionManager() {
        return context.executionManager
    }

    private class TestApplication extends AbstractApplication {
        TestApplication() {
            this.id = "app-" + ManagementContextService.ID_GENERATOR++
            displayName = "Application";

            Entity testExtraGroup = new TestGroupEntity(this, "Another group for testing");

            for(String tierName : ["tomcat tier 1", "tomcat tier 2", "data tier 1"]) {
                Entity tier = new TestGroupEntity(this, tierName);
                for(String clusterName : ["1a", "1b"]) {
                    Entity cluster = new TestGroupEntity(tier, tierName.substring(0, tierName.indexOf(" ")) +
                            " cluster " + clusterName)
                    for(int i=1; i<4; i++) {
                        if (tierName =~ /^tomcat/) {
                            Entity testTomcat = new TestTomcatEntity(cluster, "tomcat node " + clusterName + "." + i)
                            testTomcat.addGroup(testExtraGroup);
                            cluster.addOwnedChild(testTomcat)
                        } else {
                            cluster.addOwnedChild(new TestDataEntity(cluster, "data node " + clusterName + "." + i))
                        }

                    }
                    tier.addOwnedChild(cluster)
                }
                addOwnedChild(tier)
            }

            sensors.putAll([
                    Children: new BasicAttributeSensor<Integer>(Integer.class, "Children",
                            "Owned children of this application"),
                            DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
            setAttribute(getSensor("Children"), getOwnedChildren().size())
        }

        private class TestGroupEntity extends AbstractGroup {
            TestGroupEntity(Entity owner, String displayName) {
                super([:], owner)
                this.displayName = displayName
                this.id = "group-" + ManagementContextService.ID_GENERATOR++
                sensors.putAll([Children: new BasicAttributeSensor<Integer>(Integer.class, "Children",
                        "Direct children of this group"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
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
                this.locations = ["Fairbanks, Alaska", "Dubai"]

                TestEffector startDB = new TestEffector("Start DB", "This will start the database",
                        new ArrayList<ParameterType<?>>())
                TestEffector stopDB = new TestEffector("Stop DB", "This will stop the database",
                        new ArrayList<ParameterType<?>>())
                TestEffector restartDB = new TestEffector("Restart DB", "This will restart the DB",
                        new ArrayList<ParameterType<?>>())

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
                this.locations = ["Kuala Lumpur"]
                // Stealing the sensors from TomcatNode
                this.sensors.putAll(new TomcatNode().sensors)

                List<ParameterType<?>> parameterTypeList = new ArrayList<ParameterType<?>>()
                ParameterType tomcatStartLocation = new BasicParameterType("Location", Void.class)
                ParameterType actionDate = new BasicParameterType("Date", Void.class)
                parameterTypeList.add(tomcatStartLocation)
                parameterTypeList.add(actionDate)


                // Don't appear to be any effectors in TomcatNode
                TestEffector startTomcat = new TestEffector("Start Tomcat",
                                                            "This will start Tomcat at a specified location",
                                                            parameterTypeList)
                TestEffector stopTomcat = new TestEffector("Stop Tomcat",
                                                            "This will stop tomcat at its current location",
                                                            new Collections.SingletonList(actionDate))
                TestEffector restartTomcat = new TestEffector("Restart Tomcat",
                                                              "This will restart tomcat in its current location",
                                                              new ArrayList<ParameterType<?>>())

                this.effectors.putAll([  "Start Tomcat": startTomcat,
                                    "Stop Tomcat": stopTomcat,
                                    "Restart Tomcat": restartTomcat])

                this.getExecutionContext().submit([tag:this, displayName: "myTask", description: "some task or other"],
                                                  new MyRunnable(this));
            }

            protected class MyRunnable implements Runnable {
                Entity entity
                protected MyRunnable(Entity e) {
                    this.entity = e
                }
                void run() {
                    while (true) {
                        for (String key: hackMeIn.keySet()) {
                            entity.setAttribute(entity.getSensor(key),
                                                hackMeIn[key] + ManagementContextService.ID_GENERATOR +
                                                        ((int) 1000 * Math.random()))
                        }
                        Thread.sleep(5000)
                    }
                }
            }
        }
    }
}
