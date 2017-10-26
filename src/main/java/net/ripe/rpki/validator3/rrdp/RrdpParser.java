package net.ripe.rpki.validator3.rrdp;

import net.ripe.rpki.validator3.util.Sha256;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TODO We must validate XML against RelaxNG schema and reject the invalid ones.
 * TODO No session or serial number is taken into account for now, but it should be.
 */
public class RrdpParser {

    public Snapshot snapshot(final InputStream inputStream) {
        final Map<String, SnapshotObject> objects = new HashMap<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String uri = null;
            StringBuilder base64 = new StringBuilder();
            boolean inPublishElement = false;

            final Base64.Decoder decoder = Base64.getDecoder();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart().toLowerCase(Locale.ROOT);

                        if ("publish".equals(qName)) {
                            uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
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
                        final String qqName = endElement.getName().getLocalPart().toLowerCase(Locale.ROOT);
                        if ("publish".equals(qqName)) {
                            final byte[] decoded = decoder.decode(base64.toString());
                            objects.put(uri, new SnapshotObject(decoded, uri));
                            inPublishElement = false;
                            base64 = new StringBuilder();
                        }
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse snapshot: ", e);
        }
        return new Snapshot(objects);
    }

    private enum ElementCtx {
        PUBLISH, WITHDRAW
    }

    public Delta delta(final InputStream inputStream) {
        final Map<String, DeltaElement> objects = new HashMap<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String uri = null;
            String hash = null;
            StringBuilder base64 = new StringBuilder();
            boolean inPublishElement = false;

            final Base64.Decoder decoder = Base64.getDecoder();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart();

                        switch (qName) {
                            case "publish":
                                uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
                                hash = getAttr(startElement, "hash");
                                inPublishElement = true;
                                break;
                            case "withdraw":
                                uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
                                hash = getAttr(startElement, "hash", "Hash is not present in 'withdraw' element");
                                break;
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

                        switch (qqName) {
                            case "publish":
                                final byte[] decoded = decoder.decode(base64.toString());
                                objects.put(uri, new DeltaPublish(decoded, uri, Sha256.parse(hash)));
                                base64 = new StringBuilder();
                                break;
                            case "withdraw":
                                objects.put(uri, new DeltaWithdraw(uri, Sha256.parse(hash)));
                                break;
                        }
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse snapshot: ", e);
        }
        return new Delta(objects);
    }


    public Notification notification(final InputStream inputStream) {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String sessionId = null;
            BigInteger serial = null;
            String snapshotUri = null;
            String snapshotHash = null;
            final List<DeltaInfo> deltas = new ArrayList<>();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart();

                        if (qName.equalsIgnoreCase("notification")) {
                            serial = new BigInteger(getAttr(startElement, "serial", "Notification serial is not present"));
                            sessionId = getAttr(startElement, "session_id", "Session id is not present");
                        } else if (qName.equalsIgnoreCase("snapshot")) {
                            snapshotUri = getAttr(startElement, "uri", "Snapshot URI is not present");
                            snapshotHash = getAttr(startElement, "hash", "Snapshot hash is not present");
                        } else if (qName.equalsIgnoreCase("delta")) {
                            final String deltaUri = getAttr(startElement, "uri", "Delta URI is not present");
                            final String deltaHash = getAttr(startElement, "hash", "Delta hash is not present");
                            final String deltaSerial = getAttr(startElement, "serial", "Delta serial is not present");
                            deltas.add(new DeltaInfo(deltaUri, deltaHash, new BigInteger(deltaSerial)));
                        }
                        break;
                }
            }
            return new Notification(sessionId, serial, snapshotUri, snapshotHash, deltas);
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse snapshot: ", e);
        }
    }

    private String getAttr(final StartElement startElement, final String attrName, final String absentMessage) {
        final String attr = getAttr(startElement, attrName);
        if (attr == null)
            throw new RrdpException(absentMessage);
        return attr;
    }

    private String getAttr(final StartElement startElement, final String attrName) {
        final Iterator<Attribute> attributes = startElement.getAttributes();
        while (attributes.hasNext()) {
            final Attribute next = attributes.next();
            final String name = next.getName().getLocalPart();
            if (attrName.equals(name)) {
                return next.getValue();
            }
        }
        return null;
    }
}
