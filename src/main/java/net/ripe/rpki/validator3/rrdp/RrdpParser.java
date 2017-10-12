package net.ripe.rpki.validator3.rrdp;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * TODO We must validate XML agains RelaxNG schema and reject the invalid ones.
 */
public class RrdpParser {

    public Snapshot snapshot(final String xml) {
        final Map<String, RepoObject> objects = new HashMap<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = factory.createXMLEventReader(new StringReader(xml));

            String uri = null;

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
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        final Characters characters = event.asCharacters();
                        if (uri != null) {
                            final String base64 = characters.getData();
                            final byte[] decoded = decoder.decode(base64);
                            objects.put(uri, new RepoObject(decoded));
                            uri = null;
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
