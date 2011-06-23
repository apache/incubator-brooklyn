package brooklyn.event.basic;

import brooklyn.event.Event;

public interface LogEvent<T> extends Event<T> {
    /** @see Event#getSensor() */
    public LogSensor<T> getSensor();
    
	//FIXME ENGR-1458  should we add 'level' and 'topic' here?  
    //and just make this a final class extending (Basic)SensorEvent?

}