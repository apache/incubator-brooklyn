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
package brooklyn.test;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Map.Entry;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * A quick-and-simple general-purpose implementation of DynamicMBean.
 *
 * This class provides an implementation of {@link DynamicMBean}. Its initial set of attribute names and values are
 * provided to the constructor; from this it figures an {@link MBeanInfo}.
 * <p>
 * It presently assumes that all attributes are read-only; operations and notifications are not currently supported.
 * Choosing the descriptions is not supported - they are set to be the same as the name.
 * <p>
 * Getting a valid dynamic MBean (in Groovy) is as simple as:
 * <pre>
 * new GeneralisedDynamicMBean(meaning: 42, advice: "Don't panic")
 * </pre>
 */
public class GeneralisedDynamicMBean implements DynamicMBean {
    private final MBeanInfo mBeanInfo;
    private final Map<String,Object> attributes = Maps.newLinkedHashMap();
    private final Map<String,Function> operations = Maps.newLinkedHashMap();
    
    public GeneralisedDynamicMBean(Map<String,?> initialAttributes, Map<?,?> initialOperations) {
        attributes.putAll(initialAttributes);

        for (Entry<?,?> entry : initialOperations.entrySet()) {
            checkArgument(entry.getKey() instanceof String || entry.getKey() instanceof MBeanOperationInfo, "entry.key=%s", entry.getKey());
            String opName = (entry.getKey() instanceof String) ? (String)entry.getKey() : ((MBeanOperationInfo)entry.getKey()).getName();
            operations.put(opName, (Function) entry.getValue());
        }
        
        Iterable<MBeanAttributeInfo> attrInfo = Iterables.transform(initialAttributes.entrySet(), new Function<Map.Entry<String,?>, MBeanAttributeInfo>() {
            @Override public MBeanAttributeInfo apply(Map.Entry<String,?> entry) {
                return new MBeanAttributeInfo(entry.getKey(), entry.getValue().getClass().getName(), entry.getKey(), true, false, false);
            }
        });
        
        Iterable<MBeanOperationInfo> opInfo = Iterables.transform(initialOperations.keySet(), new Function<Object, MBeanOperationInfo>() {
            public MBeanOperationInfo apply(Object it) {
                if (it instanceof MBeanOperationInfo) {
                    return (MBeanOperationInfo) it;
                } else if (it instanceof CharSequence) {
                    return new MBeanOperationInfo(
                            it.toString(),
                            "my descr", 
                            new MBeanParameterInfo[0], 
                            "void", 
                            MBeanOperationInfo.ACTION_INFO);
                } else {
                    throw new IllegalArgumentException("Cannot convert "+it+" to MBeanOperationInfo");
                }
            }});
        
        mBeanInfo = new MBeanInfo(
                GeneralisedDynamicMBean.class.getName(), 
                GeneralisedDynamicMBean.class.getName(), 
                Iterables.toArray(attrInfo, MBeanAttributeInfo.class),
                new MBeanConstructorInfo[0], 
                Iterables.toArray(opInfo, MBeanOperationInfo.class),
                new MBeanNotificationInfo[0]);
    }

    public void updateAttributeValue(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String s) {
        return attributes.get(s);
    }

    @Override
    public void setAttribute(Attribute attribute) {
        attributes.put(attribute.getName(), attribute.getValue());
    }

    @Override
    public AttributeList getAttributes(String[] strings) {
        AttributeList result = new AttributeList();
        for (Object obj : mBeanInfo.getAttributes()) {
            Attribute attrib = (Attribute) obj;
            result.add(new Attribute(attrib.getName(), attributes.get(attrib.getName())));
        }
        return result;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributeList) {
        for (Object element : attributeList) {
            Attribute attrib = (Attribute) element;
            attributes.put(attrib.getName(), attrib.getValue());
        }
        return attributeList;
    }

    @Override
    public Object invoke(String s, Object[] objects, String[] strings) {
        Function op = operations.get(s);
        if (op != null) {
            return op.apply(objects);
        } else {
            throw new RuntimeException("Unknown operation "+s);
        }
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return mBeanInfo;
    }
}
