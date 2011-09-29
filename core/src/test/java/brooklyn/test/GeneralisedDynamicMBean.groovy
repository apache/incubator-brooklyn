package brooklyn.test

import java.util.Map.Entry

import javax.management.Attribute
import javax.management.AttributeList
import javax.management.DynamicMBean
import javax.management.MBeanAttributeInfo
import javax.management.MBeanInfo
import javax.management.MBeanOperationInfo
import javax.management.MBeanParameterInfo

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
class GeneralisedDynamicMBean implements DynamicMBean {
    private final MBeanInfo mBeanInfo
    private final Map attributes
    private final Map<String,Closure> operations = [:]
    
    public GeneralisedDynamicMBean(Map initialAttributes, Map initialOperations) {
        this.attributes = initialAttributes

        initialOperations.entrySet().each {
            String opName = (it.key instanceof String) ? it.key : it.key.getName()
            operations.put(opName, it.value)
        }
        
        MBeanAttributeInfo[] attrInfo = initialAttributes.entrySet().collect { Entry it ->
            new MBeanAttributeInfo(it.key, it.getValue().getClass().name, it.key, true, false, false) }
        
        List<MBeanOperationInfo> opInfo = initialOperations.keySet().collect {
            if (it instanceof MBeanOperationInfo) {
                it
            } else {
                new MBeanOperationInfo(it, "my descr", [] as MBeanParameterInfo[], "void", MBeanOperationInfo.ACTION_INFO)
            }
        }
        
        mBeanInfo = new MBeanInfo(GeneralisedDynamicMBean.class.name, GeneralisedDynamicMBean.class.name, attrInfo,
                null, opInfo as MBeanOperationInfo[], null)
    }

    public void updateAttributeValue(String name, Object value) {
        attributes[name] = value
    }

    Object getAttribute(String s) {
        return attributes[s]
    }

    void setAttribute(Attribute attribute) {
        attributes[attribute.name] = attribute.value
    }

    AttributeList getAttributes(String[] strings) {
        AttributeList result = new AttributeList()
        mBeanInfo.attributes.each { result.add(new Attribute(it.name, attributes[it.name])) }
        return result
    }

    AttributeList setAttributes(AttributeList attributeList) {
        attributeList.each { Attribute it -> attributes[it.name] = it.value }
        return attributeList
    }

    Object invoke(String s, Object[] objects, String[] strings) {
        Closure op = operations.get(s)
        if (op != null) {
            return op.call(objects)
        } else {
            throw new RuntimeException("Unknown operation "+s)
        }
    }

    MBeanInfo getMBeanInfo() {
        return mBeanInfo
    }
}
