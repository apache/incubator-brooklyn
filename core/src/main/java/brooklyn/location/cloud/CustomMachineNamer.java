package brooklyn.location.cloud;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

public class CustomMachineNamer extends CloudMachineNamer {
    
    public static final ConfigKey<String> MACHINE_NAME_TEMPLATE = ConfigKeys.newStringConfigKey("custom.machine.namer.machine", 
            "Freemarker template format for custom machine name", "#ftl\n${entity.displayName}");
    @SuppressWarnings("serial")
    public static final ConfigKey<Map<String, Object>> EXTRA_SUBSTITUTIONS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>() {}, 
            "custom.machine.namer.substitutions", "Additional substitutions to be used in the template", ImmutableMap.<String, Object>of());
    
    public CustomMachineNamer(ConfigBag setup) {
        super(setup);
    }
    
    @Override
    public String generateNewMachineUniqueName() {
        Object context = setup.peek(CloudLocationConfig.CALLER_CONTEXT);
        Entity entity = null;
        if (context instanceof Entity) entity = (Entity) context;
        
        String template = this.setup.get(MACHINE_NAME_TEMPLATE);
        
        if (!template.startsWith("#ftl\n"))
            template = "#ftl\n" + template;
        
        String processed;
        if (entity == null)
            processed = TemplateProcessor.processTemplateContents(template, this.setup.get(EXTRA_SUBSTITUTIONS));
        else
            processed = TemplateProcessor.processTemplateContents(template, (EntityInternal)entity, this.setup.get(EXTRA_SUBSTITUTIONS));
        
        return sanitize(Strings.getFragmentBetween(processed, "#ftl\n", null)).toLowerCase();
    }
    
}
