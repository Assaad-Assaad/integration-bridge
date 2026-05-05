package be.ehb.integrationbridge.shared;

import be.ehb.integrationbridge.exception.XmlSerializationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import javax.xml.stream.XMLInputFactory;
import java.util.Objects;

@Slf4j
public final class XmlUtils {

    private static final XmlMapper MAPPER = createMapper();

    private XmlUtils() {
        throw new AssertionError("Utility class — no instances");
    }

    public static String toXml(Object obj) {
        Objects.requireNonNull(obj, "Object to serialize must not be null");
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize {} to XML", obj.getClass().getSimpleName(), e);
            throw new XmlSerializationException(
                    "Failed to serialize " + obj.getClass().getSimpleName() + " to XML", e);
        }
    }

    public static <T> T fromXml(String xml, Class<T> clazz) {
        Objects.requireNonNull(clazz, "Target class must not be null");
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("XML payload must not be null or blank");
        }
        try {
            return MAPPER.readValue(xml, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize XML to {}. Payload: {}", clazz.getSimpleName(), xml, e);
            throw new XmlSerializationException(
                    "Failed to deserialize XML to " + clazz.getSimpleName(), e);
        }
    }

    private static XmlMapper createMapper() {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

        XmlMapper mapper = new XmlMapper(inputFactory);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
