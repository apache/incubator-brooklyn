package brooklyn.entity.webapp.tomcat;

import brooklyn.entity.group.DynamicCluster
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.LanguageUtils

// This class is deprecated. Instead you should write your code to refer directly to DynamicCluster.
@Deprecated
public class TomcatCluster extends DynamicCluster {
    public TomcatCluster(Map props=[:], TomcatNode template=new TomcatNode()) {
        super(getPropertiesForSuperConstructor(props), template);
    }

    private static Map getPropertiesForSuperConstructor(Map props, TomcatNode template) {
        Map superProps = [:]
        superProps << props
        superProps.newEntity = makeNewEntityClosure(template)
    }

    private void setTemplate(TomcatNode template) {
        super.newEntity = makeNewEntityClosure(template)
    }

    private static Closure<TomcatNode> makeNewEntityClosure(TomcatNode template) {
        return {
            TomcatNode copy = EntityStartUtils.cloneTemplate(template);
            copy.id = LanguageUtils.newUid()
            return copy
        }
    }
}