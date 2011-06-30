package brooklyn.test

import java.util.Map.Entry

import javax.management.Attribute
import javax.management.AttributeList
import javax.management.DynamicMBean
import javax.management.MBeanAttributeInfo
import javax.management.MBeanInfo

/**
 * A quick-and-simple general-purpose implementation of DynamicMBean.
 *
 * This class provides an implementation of @{link DynamicMBean}. Its initial set of attribute names and values are
 * provided to the constructor; from this it figures an @{link MBeanInfo}.
 *
 * It presently assumes that all attributes are read-only; operations and notifications are not currently supported.
 * Choosing the descriptions is not supported - they are set to be the same as the name.
 *
 * Getting a valid dynamic MBean (in Groovy) is as simple as:
 * new GeneralisedDynamicMBean(meaning: 42, advice: "Don't panic")
 */
class GeneralisedDynamicMBean implements DynamicMBean {

    private final MBeanInfo mBeanInfo
    private final Map attributes

    public GeneralisedDynamicMBean(Map initialAttributes) {
        this.attributes = initialAttributes

        MBeanAttributeInfo[] attrInfo = initialAttributes.entrySet().collect { Entry it ->
            new MBeanAttributeInfo(it.key, it.value.class.name, it.key, true, false, false) }
        mBeanInfo = new MBeanInfo(GeneralisedDynamicMBean.class.name, GeneralisedDynamicMBean.class.name, attrInfo,
                null, null, null)
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
        throw new RuntimeException("Not Yet Implemented")
    }

    MBeanInfo getMBeanInfo() {
        return mBeanInfo
    }
}
