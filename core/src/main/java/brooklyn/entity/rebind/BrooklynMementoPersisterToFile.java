package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.dto.BasicEntityMemento;
import brooklyn.entity.rebind.dto.BasicLocationMemento;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.mementos.BrooklynMemento;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.thoughtworks.xstream.XStream;

public class BrooklynMementoPersisterToFile extends AbstractBrooklynMementoPersister {

    // FIXME Needs synchronizing so not updating and serializing at the same time

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynMementoPersisterToFile.class);

    public interface MementoSerializer {
        String toString(BrooklynMemento memento);
        BrooklynMemento fromString(String string);
    }
    
    private final File file;
    private final MementoSerializer serializer;
    private final Object mutex = new Object();
    
    public BrooklynMementoPersisterToFile(File file, ClassLoader classLoader) {
        this.file = file;
        this.serializer = new XmlMementoSerializer(classLoader);
//        this.serializer = new JsonMementoSerializer(classLoader);
    }
    
    @Override
    public BrooklynMemento loadMemento() {
        try {
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            String xml = Joiner.on("\n").join(lines);
            return serializer.fromString(xml);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        Stopwatch stopwatch = new Stopwatch().start();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsedMillis();
            super.checkpoint(newMemento);
            long timeCheckpointed = stopwatch.elapsedMillis();
            writeMemento();
            long timeWritten = stopwatch.elapsedMillis();
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed memento; total={}ms, obtainingMutex={}ms, " +
                    "checkpointing={}ms, writing={}ms", 
                    new Object[] {timeWritten, timeObtainedMutex, (timeCheckpointed-timeObtainedMutex), 
                    (timeWritten-timeCheckpointed)});
        }
    }
    
    @Override
    public void delta(Delta delta) {
        Stopwatch stopwatch = new Stopwatch().start();
        synchronized (mutex) {
            long timeObtainedMutex = stopwatch.elapsedMillis();
            super.delta(delta);
            long timeDeltad = stopwatch.elapsedMillis();
            writeMemento();
            long timeWritten = stopwatch.elapsedMillis();
            
            if (LOG.isDebugEnabled()) LOG.debug("Checkpointed memento; total={}ms, obtainingMutex={}ms, " +
                    "delta'ing={}ms, writing={}", 
                    new Object[] {timeWritten, timeObtainedMutex, (timeDeltad-timeObtainedMutex), 
                    (timeWritten-timeDeltad)});
        }
    }
    
    private void writeMemento() {
        try {
            Files.write(serializer.toString(memento), file, Charsets.UTF_8);
        } catch (IOException e) {
            LOG.error("Failed to persist memento", e);
        }
    }
    
    private static class XmlMementoSerializer implements MementoSerializer {
        private final XStream xstream;
        private final ClassLoader classLoader;

        public XmlMementoSerializer(ClassLoader classLoader) {
            this.classLoader = checkNotNull(classLoader, "classLoader");
            xstream = new XStream();
            xstream.alias("brooklyn", MutableBrooklynMemento.class);
            xstream.alias("entity", BasicEntityMemento.class);
            xstream.alias("location", BasicLocationMemento.class);
            xstream.alias("configKey", BasicConfigKey.class);
            xstream.alias("attributeSensor", BasicAttributeSensor.class);
        }
        
        @Override
        public String toString(BrooklynMemento memento) {
            return xstream.toXML(memento);
        }

        @Override
        public BrooklynMemento fromString(String xml) {
            return (BrooklynMemento) xstream.fromXML(xml);
        }
    }
    
    private static class JsonMementoSerializer implements MementoSerializer {
        private final ClassLoader classLoader;
        private final ObjectMapper mapper;
        
        public JsonMementoSerializer(ClassLoader classLoader) {
            this.classLoader = checkNotNull(classLoader, "classLoader");
            mapper = new ObjectMapper();
            mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        }
        
        @Override
        public String toString(BrooklynMemento memento) {
            // TODO Could use input/output stream for improved efficiency
            try {
                return mapper.writeValueAsString(memento);
            } catch (JsonGenerationException e) {
                throw Throwables.propagate(e);
            } catch (JsonMappingException e) {
                throw Throwables.propagate(e);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public BrooklynMemento fromString(String json) {
            try {
                return mapper.readValue(json, MutableBrooklynMemento.class);
            } catch (JsonParseException e) {
                throw Throwables.propagate(e);
            } catch (JsonMappingException e) {
                throw Throwables.propagate(e);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
