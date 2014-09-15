package brooklyn.entity.rebind.transformer;

import java.util.Map;

import brooklyn.entity.rebind.transformer.CompoundTransformer.Builder;
import brooklyn.entity.rebind.transformer.impl.XsltTransformerTest;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.yaml.Yamls;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

@Beta
public class CompoundTransformerLoader {

    // TODO Improve error handing so get nicer errors.
    // TODO Improve names (e.g. always camel case?)
    // TODO Pass in classloader for reflectively loading rawDataTransformer?
    
    public static CompoundTransformer load(String contents) {
        CompoundTransformer.Builder builder = CompoundTransformer.builder();
        Iterable<Object> toplevel = Yamls.parseAll(contents);
        Map<String, Map<?,?>> rules = (Map<String, Map<?,?>>) ((Map<?,?>)Iterables.getOnlyElement(toplevel));
        for (Map.Entry<String, Map<?,?>> entry : rules.entrySet()) {
            addRule(builder, entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static void addRule(Builder builder, String name, Map<?,?> args) {
        if (name.equals("renameClass")) {
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameClass(oldVal, newVal);
        } else if (name.equals("renameType")) {
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameType(oldVal, newVal);
        } else if (name.equals("renameField")) {
            String clazz = (String) args.get("class_name");
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameField(clazz, oldVal, newVal);
        } else if (name.equals("xslt")) {
            String url = (String) args.get("url");
            Map<String,?> substitutions = (Map<String, ?>) args.get("substitutions");
            String xsltTemplate = ResourceUtils.create(XsltTransformerTest.class).getResourceAsString(url);
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, substitutions == null ? ImmutableMap.<String, String>of() : substitutions);
            builder.xsltTransformer(xslt);
        } else if (name.equals("rawDataTransformer")) {
            String type = (String) args.get("type");
            try {
                Class<?> clazz = CompoundTransformerLoader.class.getClassLoader().loadClass(type);
                builder.rawDataTransformer((RawDataTransformer) clazz.newInstance());
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        } else {
            throw new IllegalStateException("Unsupported transform '"+name+"' ("+args+")");
        }
    }
}
