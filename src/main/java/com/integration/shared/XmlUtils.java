package com.integration.shared;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import java.io.StringWriter;

public class XmlUtils {

    public static String toXml(Object object) {
        try {
            JAXBContext context = JAXBContext.newInstance(object.getClass());
            Marshaller marshaller = context.createMarshaller();
            
            // Zorgt voor mooie layout (indents) in de XML
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            
            StringWriter sw = new StringWriter();
            marshaller.marshal(object, sw);
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Fout bij omzetten naar XML: " + e.getMessage());
        }
    }
}