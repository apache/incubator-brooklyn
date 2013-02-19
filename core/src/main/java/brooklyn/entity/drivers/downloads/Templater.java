package brooklyn.entity.drivers.downloads;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class Templater {

    private static final Logger LOG = LoggerFactory.getLogger(Templater.class);

    public String processTemplate(String contents, Map<String,? extends Object> substitutions) throws TemplateException {
        try {
            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", contents);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();
            
            return new String(baos.toByteArray());
        } catch (IOException e) {
            LOG.warn("Error processing template '"+contents+"'", e);
            throw Exceptions.propagate(e);
        }
    }
}
