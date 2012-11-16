package brooklyn.rest.apidoc;

import java.util.Comparator;

import com.wordnik.swagger.core.DocumentationEndPoint;

public class ApidocEndpoint extends DocumentationEndPoint {

    public static final Comparator<ApidocEndpoint> COMPARATOR = new Comparator<ApidocEndpoint>() {
        @Override
        public int compare(ApidocEndpoint o1, ApidocEndpoint o2) {
            if (o1.name==o2.name) return 0;
            if (o1.name==null) return -1;
            if (o2.name==null) return 1;
            return o1.name.compareTo(o2.name);
        }
    };
    
    public final String name;
    public final String link;
    
    public ApidocEndpoint(String name, String path, String description, String link) {
        super(path, description);
        this.name = name;
        this.link = link;
    }
    
}
