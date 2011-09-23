package brooklyn.web.console.test

import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.basic.BasicAttributeSensor
import java.util.concurrent.atomic.AtomicInteger
import javax.naming.OperationNotSupportedException
import brooklyn.entity.basic.*
import brooklyn.entity.Effector
import brooklyn.management.Task
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.event.Sensor

public class TestEffector extends AbstractEffector {

    TestEffector(String name, String description, List<ParameterType<?>> parameters) {
        super(name, Void.class, parameters, description)
    }

    @Override
    Object call(Entity entity, Map parameters) {
        throw new OperationNotSupportedException("Please refrain from pressing that button")
    }
}

private class TestApplication extends AbstractApplication {
    TestApplication(Map props) {
        super(props)
        displayName = "Application";

        Entity testExtraGroup = new TestGroupEntity(this, "Another group for testing");

        for (String tierName: ["tomcat tier 1", "tomcat tier 2", "data tier 1"]) {
            Entity tier = new TestGroupEntity(this, tierName);
            for (String clusterName: ["1a", "1b"]) {
                Entity cluster = new TestGroupEntity(tier, tierName.substring(0, tierName.indexOf(" ")) +
                        " cluster " + clusterName)
                for (int i = 1; i < 4; i++) {
                    if (tierName =~ /^tomcat/) {
                        Entity testTomcat = new TestTomcatEntity(cluster, "tomcat node " + clusterName + "." + i)
                        testTomcat.addGroup(testExtraGroup);
                        cluster.addOwnedChild(testTomcat)
                        setUpAddingSensor(testTomcat)
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

    public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void setUpAddingSensor(AbstractEntity entity) {
        Runnable r = new Runnable() {
            void run() {
                while (true) {
                    Sensor sensor = new BasicAttributeSensor(Sensor.class, "test.sensor", "Added and removed every 5s")
                    entity.addSensor(sensor)
                    Thread.sleep(5*1000L)
                    entity.removeSensor(sensor.name)
                    Thread.sleep(5*1000L)
                }
            }

        };
        new Thread(r).start();
    }

    private class TestGroupEntity extends AbstractGroup {
        TestGroupEntity(Entity owner, String displayName) {
            super([:], owner)
            this.displayName = displayName
            sensors.putAll([Children: new BasicAttributeSensor<Integer>(Integer.class, "Children",
                    "Direct children of this group"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
        }

        TestGroupEntity addOwnedChild(Entity child) {
            super.addOwnedChild(child)
            setAttribute(getSensor("Children"), ownedChildren.size())
            return this
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    private class TestDataEntity extends AbstractEntity {
        TestDataEntity(Entity owner, String displayName) {
            super([:], owner)

            this.displayName = displayName
            //this.locations = ["Fairbanks, Alaska", "Dubai"]
            this.locations = [
                    new GeneralPurposeLocation([name: "US-West-1", displayName: "US-West-1", streetAddress: "Northern California, USA", description: "Northern California",
                            latitude: 40.0, longitude: -120.0]),
                    new GeneralPurposeLocation([name: "EU-West-1", displayName: "EU-West-1", streetAddress: "Dublin, Ireland, UK", description: "Dublin, Ireland",
                            latitude: 53.34778, longitude: -6.25972])
            ] //"Fairbanks,Alaska","Dubai"

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

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    private class TestTomcatEntity extends AbstractEntity {
        private Map hackMeIn = [
                "http.port": 8080,
                "webapp.tomcat.shutdownPort": 666,
                "jmx.port": 1000,
                "webapp.reqs.processing.time": 100,
                "test.sensor": 10
        ]

        public TestTomcatEntity(Entity owner, String displayName) {
            super([:], owner)
            this.displayName = displayName
            this.locations = [
                    new GeneralPurposeLocation([name: "US-East-1", displayName: "US-East-1", streetAddress: "Northern Virginia, USA", description: "Northern Virginia (approx)",
                            latitude: 38.0, longitude: -76.0]),
                    new GeneralPurposeLocation([name: "US-West-1", displayName: "US-West-1", description: "Northern California",
                            latitude: 40.0, longitude: -120.0])]
            // Stealing the sensors from TomcatNode
            this.sensors.putAll(new TomcatServer().sensors)

            List<ParameterType<?>> parameterTypeList = new ArrayList<ParameterType<?>>()
            ParameterType tomcatStartLocation = new BasicParameterType("Location", Void.class)
            ParameterType actionDate = new BasicParameterType("Date", Void.class)
            parameterTypeList.add(tomcatStartLocation)
            parameterTypeList.add(actionDate)

            // Don't appear to be any effectors in TomcatServer
            TestEffector startTomcat = new TestEffector("Start Tomcat",
                    "This will start Tomcat at a specified location",
                    parameterTypeList)
            TestEffector stopTomcat = new TestEffector("Stop Tomcat",
                    "This will stop tomcat at its current location",
                    new Collections.SingletonList(actionDate))
            TestEffector restartTomcat = new TestEffector("Restart Tomcat",
                    "This will restart tomcat in its current location",
                    new ArrayList<ParameterType<?>>())

            this.effectors.putAll(["Start Tomcat": startTomcat,
                    "Stop Tomcat": stopTomcat,
                    "Restart Tomcat": restartTomcat])

            this.getExecutionContext().submit([
                    tags: ["EFFECTOR"],
                    tag: this,
                    displayName: "Update values",
                    description: "This updates sensor values"],
                    new MyRunnable(this));
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }

        protected class MyRunnable implements Runnable {
            Entity entity

            protected MyRunnable(Entity e) {
                this.entity = e
            }

            void run() {
                while (true) {
                    for (String key: hackMeIn.keySet()) {
                        if(entity.getSensor(key) != null){
                            entity.setAttribute(entity.getSensor(key), hackMeIn[key] + ((int) 1000 * Math.random()))
                        }
                    }
                    Thread.sleep(5000)
                }
            }
        }
    }
}