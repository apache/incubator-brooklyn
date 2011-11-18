package brooklyn.gemfire.api;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterParser {

    public Map<String,Object> parse(String query) throws UnsupportedEncodingException {
        Map<String,Object> result = new HashMap<String, Object>();

        if (query != null) {
             String pairs[] = query.split("[&]");

             for (String pair : pairs) {
                 String param[] = pair.split("[=]");

                 String key = null;
                 String value = null;
                 if (param.length > 0) {
                     key = URLDecoder.decode(param[0],System.getProperty("file.encoding"));
                 }

                 if (param.length > 1) {
                     value = URLDecoder.decode(param[1],System.getProperty("file.encoding"));
                 }

                 if (result.containsKey(key)) {
                     Object obj = result.get(key);
                     if(obj instanceof List<?>) {
                         List<String> values = (List<String>)obj;
                         values.add(value);
                     } else if(obj instanceof String) {
                         List<String> values = new ArrayList<String>();
                         values.add((String)obj);
                         values.add(value);
                         result.put(key, values);
                     }
                 } else {
                     result.put(key, value);
                 }
             }
         }

        return result;
    }

}
