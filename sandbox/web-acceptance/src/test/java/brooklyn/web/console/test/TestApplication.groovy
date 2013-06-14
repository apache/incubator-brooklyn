package brooklyn.web.console.test

import javax.naming.OperationNotSupportedException

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.*
import brooklyn.entity.webapp.tomcat.TomcatServerImpl
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task

public class TestEffector extends AbstractEffector {

    TestEffector(String name, String description, List<ParameterType<?>> parameters) {
        super(name, Void.class, parameters, description)
    }

    @Override
    Object call(Entity entity, Map parameters) {
        throw new OperationNotSupportedException("Please refrain from pressing that button")
    }
}

class TestApplication extends AbstractApplication {
    public static final BasicAttributeSensor<Integer> CHILDREN = new BasicAttributeSensor<Integer>(Integer.class, "Children",
            "Children of this application");
        
    public static final BasicAttributeSensor<String> DATA_RATE = new BasicAttributeSensor<String>(String.class, "DataRate");

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
                        cluster.addChild(testTomcat)
                        setUpAddingSensor(testTomcat)
                    } else {
                        cluster.addChild(new TestDataEntity(cluster, "data node " + clusterName + "." + i))
                    }

                }
                tier.addChild(cluster)
            }
            addChild(tier)
        }

        setAttribute(CHILDREN, getChildren().size())
    }

    @Override
    public void init() {
        // no-op
    }

    public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void setUpAddingSensor(AbstractEntity entity) {
        Runnable r = new Runnable() {
            void run() {
                while (true) {
                    Sensor sensor = new BasicAttributeSensor(Sensor.class, "test.sensor", "Added and removed every 5s")
                    entity.getMutableEntityType().addSensor(sensor)
                    Thread.sleep(5*1000L)
                    entity.getMutableEntityType().removeSensor(sensor.name)
                    Thread.sleep(5*1000L)
                }
            }

        };
        new Thread(r).start();
    }

    public static class TestGroupEntity extends AbstractGroupImpl {
        public static final BasicAttributeSensor<Integer> CHILDREN = new BasicAttributeSensor<Integer>(Integer.class, "Children",
                "Direct children of this group");
        public static final BasicAttributeSensor<String> DATA_RATE = new BasicAttributeSensor<String>(String.class, "DataRate");
            
        TestGroupEntity(Entity parent, String displayName) {
            super([:], parent)
            this.displayName = displayName
        }

        TestGroupEntity addChild(Entity child) {
            super.addChild(child)
            setAttribute(CHILDREN, getChildren().size())
            return this
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    public static class TestDataEntity extends AbstractEntity {
        public static final BasicAttributeSensor<String> HAPPINESS = new BasicAttributeSensor<String>(String.class, "Happiness");
        public static final BasicAttributeSensor<String> CACHE = new BasicAttributeSensor<String>(String.class, "Cache", "Some cache metric");
        public static final BasicAttributeSensor<String> SYNC = new BasicAttributeSensor<String>(String.class, "Sync", "Synchronization strategy");

        public static final TestEffector START_DB = new TestEffector("Start DB", "This will start the database",
                new ArrayList<ParameterType<?>>());
        public static final TestEffector STOP_DB = new TestEffector("Stop DB", "This will stop the database",
                new ArrayList<ParameterType<?>>());
        public static final TestEffector RESTART_DB = new TestEffector("Restart DB", "This will restart the DB",
                new ArrayList<ParameterType<?>>());

        TestDataEntity(Entity parent, String displayName) {
            super([:], parent)

            this.displayName = displayName
            //this.locations = ["Fairbanks, Alaska", "Dubai"]
            this.locations = [
                    new SimulatedLocation([name: "US-West-1", displayName: "US-West-1", streetAddress: "Northern California, USA", description: "Northern California",
                            latitude: 40.0, longitude: -120.0]),
                    new SimulatedLocation([name: "EU-West-1", displayName: "EU-West-1", streetAddress: "Dublin, Ireland, UK", description: "Dublin, Ireland",
                            latitude: 53.34778, longitude: -6.25972])
            ] //"Fairbanks,Alaska","Dubai"

            setAttribute(HAPPINESS, 50)
            setAttribute(CACHE, 200)
            setAttribute(SYNC, "Moop")
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    public static class TestTomcatEntity extends AbstractEntity {
		
		static List<ParameterType<?>> parameterTypeList = new ArrayList<ParameterType<?>>()
		static ParameterType tomcatStartLocation = new BasicParameterType("Location", Void.class)
		static ParameterType actionDate = new BasicParameterType("Date", Void.class)

        public static final TestEffector START_TOMCAT = new TestEffector("Start Tomcat",
                "This will start Tomcat at a specified location",
                parameterTypeList);
        public static final TestEffector STOP_TOMCAT = new TestEffector("Stop Tomcat",
                "This will stop tomcat at its current location",
                new Collections.SingletonList(actionDate));
        public static final TestEffector RESTART_TOMCAT = new TestEffector("Restart Tomcat",
                "This will restart tomcat in its current location",
                new ArrayList<ParameterType<?>>());

        private Map hackMeIn = [
                "http.port": 8080,
                "webapp.tomcat.shutdownPort": 666,
                "jmx.port": 1000,
                "webapp.reqs.processing.time": 100,
                "test.sensor": 10
        ]

        public TestTomcatEntity(Entity parent, String displayName) {
            super([:], parent)
            this.displayName = displayName
            this.locations = [
                    new SimulatedLocation([name: "US-East-1", displayName: "US-East-1", streetAddress: "Northern Virginia, USA", description: "Northern Virginia (approx)",
                            latitude: 38.0, longitude: -76.0]),
                    new SimulatedLocation([name: "US-West-1", displayName: "US-West-1", description: "Northern California",
                            latitude: 40.0, longitude: -120.0])]
            
            // Stealing the sensors from TomcatNode
            getMutableEntityType().addSensors(new TomcatServerImpl().getEntityType().getSensors())

            parameterTypeList.add(tomcatStartLocation)
            parameterTypeList.add(actionDate)

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