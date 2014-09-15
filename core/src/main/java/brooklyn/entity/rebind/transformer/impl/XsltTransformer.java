package brooklyn.entity.rebind.transformer.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import brooklyn.entity.rebind.transformer.RawDataTransformer;

import com.google.common.annotations.Beta;

@Beta
public class XsltTransformer implements RawDataTransformer {

    private final TransformerFactory factory;
    private final StreamSource xslt;

    public XsltTransformer(String xsltContent) {
        factory = TransformerFactory.newInstance();
        xslt = new StreamSource(new ByteArrayInputStream(xsltContent.getBytes()));
    }
    
    public String transform(String input) throws IOException, URISyntaxException, TransformerException {
        Transformer transformer = factory.newTransformer(xslt);
        
        Source text = new StreamSource(new ByteArrayInputStream(input.getBytes()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length());
        transformer.transform(text, new StreamResult(baos));
        
        return new String(baos.toByteArray());
    }
}
