package brooklyn.util.internal;

import groovy.transform.InheritConstructors
import groovy.util.ObservableList
import groovy.util.ObservableMap

import java.io.Serializable

//FIXME intermittent bogus errors about InheritConstructors not supported -- IDE only
//and sometimes about Inconsistent classfile encountered;
//placing them in separate classes doesn't help, need to pursue with Groovy team

@InheritConstructors
public class SerializableObservableList extends ObservableList implements Serializable {}

@InheritConstructors
public class SerializableObservableMap extends ObservableMap implements Serializable {}
