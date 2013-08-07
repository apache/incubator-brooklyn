package brooklyn.util.text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

public class TemplateProcessor {

   private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

   public static String processTemplate(String templateFileName, Map<String, ? extends Object> substitutions) {
      String templateContents;
      try {
         templateContents = Files.toString(new File(templateFileName), Charsets.UTF_8);
      } catch (IOException e) {
         log.warn("Error loading file " + templateFileName, e);
         throw Exceptions.propagate(e);
      }
      return processTemplateContents(templateContents, substitutions);
   }

   public static String processTemplateContents(String templateContents, Map<String, ? extends Object> substitutions) {
      try {
         Configuration cfg = new Configuration();
         StringTemplateLoader templateLoader = new StringTemplateLoader();
         templateLoader.putTemplate("config", templateContents);
         cfg.setTemplateLoader(templateLoader);
         Template template = cfg.getTemplate("config");

         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         Writer out = new OutputStreamWriter(baos);
         template.process(substitutions, out);
         out.flush();

         return new String(baos.toByteArray());
      } catch (Exception e) {
         log.warn("Error processing template " + templateContents + " with vars " + substitutions, e);
         throw Exceptions.propagate(e);
      }
   }
}
