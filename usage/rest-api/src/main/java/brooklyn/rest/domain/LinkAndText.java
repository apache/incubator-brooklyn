package brooklyn.rest.domain;

import org.codehaus.jackson.annotate.JsonProperty;

public class LinkAndText {

    final String link;
    final String text;
    
    public LinkAndText(@JsonProperty("link") String link, @JsonProperty("text") String text) {
        this.link = link;
        this.text = text;
    }

    public String getLink() {
        return link;
    }
    
    public String getText() {
        return text;
    }
    
}
