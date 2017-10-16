package net.ripe.rpki.validator3.rrdp;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * TODO We must validate XML against RelaxNG schema and reject the invalid ones.
 * TODO No session or serial number is taken into account for now, but it should be.
 */
public class RrdpParser {

    public Snapshot snapshot(final String xml) {
        final Map<String, RepoObject> objects = new HashMap<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = factory.createXMLEventReader(new StringReader(xml));

            String uri = null;
            StringBuilder base64 = new StringBuilder();
            boolean inPublishElement = false;

            final Base64.Decoder decoder = Base64.getDecoder();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart();

                        if (qName.equalsIgnoreCase("publish")) {
                            Iterator<Attribute> attributes = startElement.getAttributes();
                            uri = attributes.next().getValue();
                            inPublishElement = true;
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        final Characters characters = event.asCharacters();
                        if (inPublishElement) {
                            final String thisBase64 = characters.getData();
                            base64.append(thisBase64.replaceAll("\\s", ""));
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        final EndElement endElement = event.asEndElement();
                        final String qqName = endElement.getName().getLocalPart();

                        if (qqName.equalsIgnoreCase("publish")) {
                            final byte[] decoded = decoder.decode(base64.toString());
                            objects.put(uri, new RepoObject(decoded));
                            inPublishElement = false;
                        }
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse snapshot: ", e);
        }
        return new Snapshot(objects);
    }
}
