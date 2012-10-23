package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import com.google.common.base.Throwables;

public class JsonMementoSerializer<T> implements MementoSerializer<T> {
    private final Class<? extends T> deserializationType;
    private final ClassLoader classLoader;
    private final ObjectMapper mapper;
    
    public JsonMementoSerializer(Class<? extends T> deserializationType, ClassLoader classLoader) {
        this.deserializationType = checkNotNull(deserializationType, "deserializationType");
        this.classLoader = checkNotNull(classLoader, "classLoader");
        mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }
    
    @Override
    public String toString(T memento) {
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
    public T fromString(String json) {
        try {
            return mapper.readValue(json, deserializationType);
        } catch (JsonParseException e) {
            throw Throwables.propagate(e);
        } catch (JsonMappingException e) {
            throw Throwables.propagate(e);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}