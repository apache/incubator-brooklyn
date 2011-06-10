package org.overpaas.util;

import groovy.transform.InheritConstructors;
import groovy.util.ObservableList;
import groovy.util.ObservableMap;

import java.io.Serializable;

public class SerializableObservables {

	@InheritConstructors
	public static class SerializableObservableList extends ObservableList implements Serializable {} 

	@InheritConstructors
	public static class SerializableObservableMap extends ObservableMap implements Serializable {} 

}
