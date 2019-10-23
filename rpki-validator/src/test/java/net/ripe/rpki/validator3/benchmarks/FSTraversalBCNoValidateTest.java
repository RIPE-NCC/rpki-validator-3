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
package net.ripe.rpki.validator3.benchmarks;

import com.google.common.io.Closer;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.crypto.util.BouncyCastleUtil;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.validator3.util.Bench;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.jooq.lambda.Unchecked;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Ignore
public class FSTraversalBCNoValidateTest {


    @Test
    public void testTraversal() throws Exception {
        final String locationUri = "rsync://rpki.ripe.net/repository/";
        System.out.println("Fetching " + locationUri);
        final File targetDirectory = fetch(locationUri);
        System.out.println("Traversing first time " + targetDirectory.toPath());

        final Long t1 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, true)));
        System.out.println("First time took : " + t1 + "ms " + Bench.dump("global"));

        System.out.println("Traversing second time " + targetDirectory.toPath());
        // do it twice to avoid FS caching influence
        final Long t2 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, true)));

        System.out.println("Second time took : " + t2 + "ms " + Bench.dump("global"));

    }

    private File fetch(String locationUri) throws IOException {
        final File targetDirectory = new File("/tmp/traverse-test");
        if(!targetDirectory.exists()) Files.createDirectories(targetDirectory.toPath());
        System.out.println("Fetching to targetDirectory = " + targetDirectory);
        if (targetDirectory.list().length == 0) {
            final Rsync rsync = new Rsync(locationUri, targetDirectory.getPath());
            rsync.addOptions("--update", "--times", "--copy-links", "--recursive", "--delete");
            rsync.execute();
        }
        return targetDirectory;
    }


    private void traverse(File targetDirectory, String locationUri, boolean createObjects) throws IOException {
        AtomicInteger countCer = new AtomicInteger(0);
        AtomicInteger countCms = new AtomicInteger(0);
        AtomicInteger countCrl = new AtomicInteger(0);
        Files.walkFileTree(targetDirectory.toPath(), new SimpleFileVisitor<Path>() {
            private URI currentLocation = URI.create(locationUri);

            // Pre and post visit maintains validationResult location to be up to date with actual dir being visited.
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(targetDirectory.toPath())) {
                    return FileVisitResult.CONTINUE;
                }
                super.preVisitDirectory(dir, attrs);
                currentLocation = currentLocation.resolve(dir.getFileName().toString() + "/");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.equals(targetDirectory.toPath())) {
                    return FileVisitResult.CONTINUE;
                }
                super.postVisitDirectory(dir, exc);
                currentLocation = currentLocation.resolve("..").normalize();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                super.visitFile(file, attrs);

                final byte[] content = Files.readAllBytes(file);
                final byte[] sha256 = Sha256.hash(content);

                final String key = Hex.format(sha256);
                final String location = currentLocation.toString();
                log.info("Visiting {}", location);

                String blobFileName = file.toString();
                if (isCMS(blobFileName)) {

                    ASN1Primitive cms_creation = Bench.mark("CMS creation", () -> parseCMSNoValidate(content, location));
                    if (cms_creation != null) {
                        countCms.incrementAndGet();
                    } else {
                        log.info("Unparseable cms {}", location);
                    }

                } else if (blobFileName.endsWith(".crl")) {
                    X509CRL x509CRL = Bench.mark("Crl creation", () -> parseCRLNoValidate(content));
                    if (x509CRL != null) {
                        countCrl.incrementAndGet();
                    } else {
                        log.info("Unparseable crl {}", location);
                    }

                } else if (blobFileName.endsWith(".cer")) {
                    X509Certificate x509Certificate = Bench.mark("Cert creation", () -> parseX509CertificateNoValidate(content));
                    if (x509Certificate != null) {
                        countCer.incrementAndGet();
                    } else {
                        log.info("Unparseable cer {}", location);
                    }

                } else {
                    log.info("Unknown blob! {}", location);
                }

                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Processed " + countCer.get() + " certificates " + countCrl + " crls " + countCms + " CMS (Roa, Gbr, Mft)");
    }

    static final List<String> cmsSuffixes = Arrays.asList(".roa", ".mft", ".gbr");
    static boolean isCMS(String location) {
        for (String suffix : cmsSuffixes) {
            if (location.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private ASN1Primitive parseCMSNoValidate(byte[] content, String location) {
        {
            try {
                CMSSignedDataParser sp = new CMSSignedDataParser(BouncyCastleUtil.DIGEST_CALCULATOR_PROVIDER, content);
                final CMSTypedStream signedContent = sp.getSignedContent();

                try (InputStream signedContentStream = signedContent.getContentStream()) {
                    return decodeRawContent(signedContentStream);

                } catch (IOException e) {

                    return null;
                }
            } catch (CMSException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    // In RPKI Commons this is where the actual CMS specific validation is done.
    // Meaning different validation for MFT, GBR, and ROA.
    private ASN1Primitive decodeRawContent(InputStream content) {
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(content)) {
            return asn1InputStream.readObject();
        } catch (IOException e) {

        }
        return null;
    }


    static X509CRL parseCRLNoValidate(byte[] encoded) {
        final X509CRL crl;
        if (null != encoded) {
            try {
                final Closer closer = Closer.create();
                try {
                    final ByteArrayInputStream in = new ByteArrayInputStream(encoded);
                    final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    crl = (X509CRL) factory.generateCRL(in);
                } catch (final CertificateException | CRLException e) {
                    throw closer.rethrow(new IllegalArgumentException(e));
                } catch (final Throwable t) {
                    throw closer.rethrow(t);
                } finally {
                    closer.close();
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error managing CRL I/O stream", e);
            }
        } else {
            crl = null;
        }
        return crl;

    }

    public static X509Certificate parseX509CertificateNoValidate(byte[] encoded) {
        try {
            final Closer closer = Closer.create();
            try {
                final InputStream input = closer.register(new ByteArrayInputStream(encoded));
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory.generateCertificate(input);
            } catch (final CertificateException e) {
                return null;
            } catch (final Throwable t) {
                throw closer.rethrow(t);
            }
        } catch (final IOException e) {
            return null;
        }
    }


}