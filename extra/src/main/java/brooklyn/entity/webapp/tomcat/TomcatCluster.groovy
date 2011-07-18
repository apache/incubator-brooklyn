package brooklyn.entity.webapp.tomcat;

import brooklyn.entity.group.DynamicCluster
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.LanguageUtils

// This class is deprecated. Instead you should write your code to refer directly to DynamicCluster.
@Deprecated
public class TomcatCluster extends DynamicCluster {
    public TomcatCluster(Map props=[:], TomcatServer template=new TomcatServer()) {
        super(getPropertiesForSuperConstructor(props), template);
    }

    private static Map getPropertiesForSuperConstructor(Map props, TomcatServer template) {
        Map superProps = [:]
        superProps << props
        superProps.newEntity = makeNewEntityClosure(template)
    }

    private void setTemplate(TomcatServer template) {
        super.newEntity = makeNewEntityClosure(template)
    }

    private static Closure<TomcatServer> makeNewEntityClosure(TomcatServer template) {
        return {
            TomcatServer copy = EntityStartUtils.cloneTemplate(template);
            copy.id = LanguageUtils.newUid()
            return copy
        }
    }
}