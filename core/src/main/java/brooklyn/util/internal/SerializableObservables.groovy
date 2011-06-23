package brooklyn.util.internal;

import groovy.util.ObservableList
import groovy.util.ObservableMap

import java.io.Serializable
import java.util.List

//FIXME intermittent bogus errors about InheritConstructors not supported -- IDE only
//and sometimes about Inconsistent classfile encountered;
//placing them in separate classes doesn't help, need to pursue with Groovy team

//@InheritConstructors

//@InheritConstructors
public class SerializableObservableList extends ObservableList implements Serializable {
	private static final long serialVersionUID = 8299640601890557184L;
	public SerializableObservableList(List delegate) { super(delegate) }
}

//@InheritConstructors
public class SerializableObservableMap extends ObservableMap implements Serializable {
	private static final long serialVersionUID = 6731953672699661734L;
	public SerializableObservableMap(Map delegate) { super(delegate) }
}
