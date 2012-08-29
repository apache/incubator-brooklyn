package brooklyn.web.console

import static org.testng.Assert.*
import static org.testng.Assert.*
import groovy.json.JsonSlurper
import groovy.transform.InheritConstructors

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation;

public class EntityControllerTest extends grails.test.ControllerUnitTestCase {
    
    // FIXME Does not work yet. The calls such as `taskSummary as JSON` fails with
    // Cannot cast object ... with class ... to class 'grails.converters.JSON'
    
    EntityService entityService
    MyApp app
    Location loc
    
//    @BeforeMethod
//    public void setupController() {
//        super.setUp()
//
//        entityService = new EntityService()
//        loc = new SimulatedLocation([latitude: 56, longitude: -2.5]);
//        app = new MyApp()
//        app.start([loc])
//        entityService.managementContextService = app.managementContext
//
//        controller.entityService = entityService
//    }
//
//    @AfterMethod
//    public void tearDown() {
//        super.tearDown()
//    }
    
//    @Test(enabled=false)
//    void testRetrievesEntityInfo() {
//        controller.params.id = app.id
//        controller.info()
//        Object result = new JsonSlurper().parseText(controller.response.contentAsString)
//        assertEquals(result.id, app.id)
//        assertEquals(result.displayName, app.displayName)
//    }
//
//    @Test(enabled=false)
//    void testRetrievingEntityInfoForNonExistantEntityGives404() {
//        controller.params.id = "doesnotexist"
//        controller.info()
//        assertEquals(404, controller.response.status)
//    }
//
//    // FIXME actually returns json of collection of SensorSummary objects, rather than map
//    @Test(enabled=false)
//    void testSerializeSensorOfTypeEnum() {
//        app.setAttribute(MyApp.MY_ENUM_SIMPLE, MyEnumSimple.B)
//        app.setAttribute(MyApp.MY_ENUM, MyEnumWithGetterMethod.A)
//        controller.params.id = app.id
//        controller.sensors()
//        Object result = new JsonSlurper().parseText(controller.response.contentAsString)
//        assertEquals(result.myenumWithGetterMethod, "A")
//        assertEquals(result.myenumSimple, "A")
//    }
}

@InheritConstructors
public class MyApp extends AbstractApplication {
    public static final BasicAttributeSensor<MyEnumSimple> MY_ENUM_SIMPLE = [ MyEnumSimple.class, "myenumSimple", "My enum simple" ]
    public static final BasicAttributeSensor<MyEnumWithGetterMethod> MY_ENUM = [ MyEnumWithGetterMethod.class, "myenumWithGetterMethod", "My enum" ]
}
