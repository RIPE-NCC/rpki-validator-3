/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.rrdp;

import lombok.Value;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.util.Hex;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * TODO We must validate XML against RelaxNG schema and reject the invalid ones.
 * TODO No session or serial number is taken into account for now, but it should be.
 */
public class RrdpParser {

    @Value
    public static class SnapshotHeader {
        String sessionId;
        BigInteger serial;
    }

    @Value
    public static class DeltaHeader {
        String sessionId;
        BigInteger serial;
    }

    public void parseSnapshot(InputStream inputStream, Consumer<SnapshotHeader> processSnapshotHeader, Consumer<SnapshotObject> processSnapshotObject) {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            final XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String sessionId = null;
            BigInteger serial = null;
            String uri = null;
            StringBuilder base64 = new StringBuilder();
            boolean inPublishElement = false;
            boolean snapshotHeaderProcessed = false;

            final Base64.Decoder decoder = Base64.getDecoder();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart().toLowerCase(Locale.ROOT);

                        switch (qName) {
                            case "publish":
                                if (!snapshotHeaderProcessed) {
                                    throw new RrdpException(ErrorCodes.RRDP_PARSE_ERROR, "snapshot header not present before published objects");
                                }
                                uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
                                inPublishElement = true;
                                break;
                            case "snapshot":
                                serial = new BigInteger(getAttr(startElement, "serial", "Notification serial is not present"));
                                sessionId = getAttr(startElement, "session_id", "Session id is not present");
                                processSnapshotHeader.accept(new SnapshotHeader(sessionId, serial));
                                snapshotHeaderProcessed = true;
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
                        final String qqName = endElement.getName().getLocalPart().toLowerCase(Locale.ROOT);
                        if ("publish".equals(qqName)) {
                            final byte[] decoded = decoder.decode(base64.toString());
                            processSnapshotObject.accept(new SnapshotObject(decoded, uri));
                            inPublishElement = false;
                            base64 = new StringBuilder();
                        }
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse snapshot: ", e);
        }
    }

    public void parseDelta(InputStream inputStream, Consumer<DeltaHeader> processDeltaHeader, Consumer<DeltaElement> processDeltaElement) {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            final XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String sessionId = null;
            BigInteger serial = null;
            String uri = null;
            String hash = null;
            StringBuilder base64 = new StringBuilder();
            boolean inPublishElement = false;
            boolean deltaHeaderProcessed = false;

            final Base64.Decoder decoder = Base64.getDecoder();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart();

                        switch (qName) {
                            case "publish":
                                if (!deltaHeaderProcessed) {
                                    throw new RrdpException(ErrorCodes.RRDP_PARSE_ERROR, "delta header not present before elements");
                                }
                                uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
                                hash = getAttr(startElement, "hash");
                                inPublishElement = true;
                                break;
                            case "withdraw":
                                if (!deltaHeaderProcessed) {
                                    throw new RrdpException(ErrorCodes.RRDP_PARSE_ERROR, "delta header not present before elements");
                                }
                                uri = getAttr(startElement, "uri", "Uri is not present in 'publish' element");
                                hash = getAttr(startElement, "hash", "Hash is not present in 'withdraw' element");
                                break;
                            case "delta":
                                serial = new BigInteger(getAttr(startElement, "serial", "Notification serial is not present"));
                                sessionId = getAttr(startElement, "session_id", "Session id is not present");
                                processDeltaHeader.accept(new DeltaHeader(sessionId, serial));
                                deltaHeaderProcessed = true;
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
                                base64 = new StringBuilder();
                                processDeltaElement.accept(new DeltaPublish(decoded, uri, Hex.parse(hash)));
                                break;
                            case "withdraw":
                                processDeltaElement.accept(new DeltaWithdraw(uri, Hex.parse(hash)));
                                break;
                        }
                        break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse delta: ", e);
        }
    }


    public Notification notification(final InputStream inputStream) {
        try {
            final XMLInputFactory factory = XMLInputFactory.newInstance();
            final XMLEventReader eventReader = factory.createXMLEventReader(inputStream);

            String sessionId = null;
            BigInteger serial = null;
            String snapshotUri = null;
            String snapshotHash = null;
            final List<DeltaInfo> deltas = new ArrayList<>();

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                if (event.getEventType() == XMLStreamConstants.START_ELEMENT) {
                    final StartElement startElement = event.asStartElement();
                    final String qName = startElement.getName().getLocalPart();

                    switch (qName) {
                        case "notification":
                            serial = new BigInteger(getAttr(startElement, "serial", "Notification serial is not present"));
                            sessionId = getAttr(startElement, "session_id", "Session id is not present");
                            break;
                        case "snapshot":
                            snapshotUri = getAttr(startElement, "uri", "Snapshot URI is not present");
                            snapshotHash = getAttr(startElement, "hash", "Snapshot hash is not present");
                            break;
                        case "delta":
                            final String deltaUri = getAttr(startElement, "uri", "Delta URI is not present");
                            final String deltaHash = getAttr(startElement, "hash", "Delta hash is not present");
                            final String deltaSerial = getAttr(startElement, "serial", "Delta serial is not present");
                            deltas.add(new DeltaInfo(deltaUri, deltaHash, new BigInteger(deltaSerial)));
                            break;
                    }
                }
            }
            return new Notification(sessionId, serial, snapshotUri, snapshotHash, deltas);
        } catch (XMLStreamException e) {
            throw new RrdpException("Couldn't parse notification: ", e);
        }
    }

    private String getAttr(final StartElement startElement, final String attrName, final String noAttrMessage) {
        final String attr = getAttr(startElement, attrName);
        if (attr == null)
            throw new RrdpException(ErrorCodes.RRDP_PARSE_ERROR, noAttrMessage);
        return attr;
    }

    private String getAttr(final StartElement startElement, final String attrName) {
        final Iterator<?> attributes = startElement.getAttributes();
        while (attributes.hasNext()) {
            final Attribute next = (Attribute) attributes.next();
            final String name = next.getName().getLocalPart();
            if (attrName.equals(name)) {
                return next.getValue();
            }
        }
        return null;
    }
}
