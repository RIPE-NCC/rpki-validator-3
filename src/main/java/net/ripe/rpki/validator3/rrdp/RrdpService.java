package net.ripe.rpki.validator3.rrdp;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.cms.manifest.ManifestCmsParser;
import net.ripe.rpki.commons.crypto.crl.X509Crl;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificate;
import net.ripe.rpki.commons.crypto.x509cert.X509ResourceCertificateParser;
import net.ripe.rpki.validator3.domain.RpkiObject;
import net.ripe.rpki.validator3.domain.RpkiObjectRepository;
import net.ripe.rpki.validator3.util.Sha256;
import org.bouncycastle.jce.provider.X509CRLParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;

@Service
@Slf4j
public class RrdpService {

    private RrdpParser rrdpParser = new RrdpParser();

    private RrdpClient rrdpClient;

    private RpkiObjectRepository rpkiObjectRepository;

    @Autowired
    public RrdpService(final RrdpClient rrdpClient, final RpkiObjectRepository rpkiObjectRepository) {
        this.rrdpClient = rrdpClient;
        this.rpkiObjectRepository = rpkiObjectRepository;
    }

    public void storeSnapshot(final String snapshotUrl) {
        final String snapshotXml = rrdpClient.getSnapshot(snapshotUrl);
        final Snapshot snapshot = rrdpParser.snapshot(snapshotXml);
        snapshot.asMap().forEach((uri, value) -> rpkiObjectRepository.add(createRpkiObject(uri, value.content)));
    }

    private RpkiObject createRpkiObject(final String uri, final byte[] content) {
        // TODO serialNumber must be taken from the parsed object
        try {
            if (endsWith(uri,".cer")) {
                final X509ResourceCertificateParser parser = new X509ResourceCertificateParser();
                parser.parse(uri, content);
                if (parser.isSuccess()) {
                    final X509ResourceCertificate certificate = parser.getCertificate();
                    final byte[] sha256 = Sha256.hash(content);
                    return new RpkiObject(Collections.singletonList(uri), certificate.getSerialNumber(), sha256, content);
                }
                return null;
            } else if (endsWith(uri,".mft")) {
                final ManifestCmsParser parser = new ManifestCmsParser();
                parser.parse(uri, content);
                if (parser.isSuccess()) {
                    final X509ResourceCertificate certificate = parser.getManifestCms().getCertificate();
                    final byte[] sha256 = Sha256.hash(content);
                    return new RpkiObject(Collections.singletonList(uri), certificate.getSerialNumber(), sha256, content);
                }
                return null;
            } else if (endsWith(uri, ".crl")) {
                final X509Crl crl = new X509Crl(content);
                final byte[] sha256 = Sha256.hash(content);
                // TODO Find serial number
                return new RpkiObject(Collections.singletonList(uri), BigInteger.ONE, sha256, content);

            } else if (endsWith(uri, ".roa")) {
            }

            final BigInteger serialNumber = BigInteger.ONE;
            final byte[] sha256 = Sha256.hash(content);
            return new RpkiObject(Collections.singletonList(uri), serialNumber, sha256, content);
        } catch (IOException e) {
            log.error("Couldn't parse and store object with URI " + uri);
        }
        // TODO Do something different
        return null;
    }

    private boolean endsWith(final String s, final String end) {
        if (s.length() < end.length())
            return false;
        return s.substring(s.length() - 3, s.length()).equalsIgnoreCase(end);
    }

}
