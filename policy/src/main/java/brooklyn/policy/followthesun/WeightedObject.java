/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.followthesun;

public class WeightedObject<T> implements Comparable<WeightedObject<T>>{
	
	final T object;
	final double weight;
	
	public WeightedObject(T obj, double weight) {
		this.object = obj;
		this.weight = weight;
	}
	
	public T getObject() {
		return object;
	}
	
	public double getWeight() {
		return weight;
	}

	/**
	 * Note that equals and compareTo are not consistent: x.compareTo(y)==0 iff x.equals(y) is 
	 * highly recommended in Java, but is not required. This can make TreeSet etc behave poorly...
	 */
	public int compareTo(WeightedObject<T> o) {
		double diff = o.getWeight() - weight;
		if (diff>0.0000000000000001) return -1;
		if (diff<-0.0000000000000001) return 1;
		return 0;
	}

	@Override
	/** true irrespective of weight */
	public boolean equals(Object obj) {
		if (!(obj instanceof WeightedObject<?>)) return false;
		if (getObject()==null) {
			return ((WeightedObject<?>)obj).getObject() == null;
		} else {
			return getObject().equals( ((WeightedObject<?>)obj).getObject() );
		}
	}
	
	@Override
	public int hashCode() {
		if (getObject()==null) return 234519078;
		return getObject().hashCode();
	}
	
	@Override
	public String toString() {
		return ""+getObject()+"["+getWeight()+"]";
	}
}
