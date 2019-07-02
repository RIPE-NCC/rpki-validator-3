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

import fj.data.Either;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.commons.rsync.Rsync;
import net.ripe.rpki.commons.validation.ValidationResult;
import net.ripe.rpki.validator3.domain.RpkiObjectUtils;
import net.ripe.rpki.validator3.storage.data.RpkiObject;
import net.ripe.rpki.validator3.util.Bench;
import net.ripe.rpki.validator3.util.Hex;
import net.ripe.rpki.validator3.util.Sha256;
import net.ripe.rpki.validator3.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.lambda.Unchecked;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
//@Ignore
public class FSTraversalTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testTraversal() throws Exception {
        final String locationUri = "rsync://rpki.ripe.net/repository/";
        final File targetDirectory = fetch(locationUri);
        final Long t1 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, true)));
        final Long t2 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, false)));

        // do it twice to avoid FS caching influence
        final Long t3 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, true)));
        final Long t4 = Time.timed(Unchecked.runnable(() -> traverse(targetDirectory, locationUri, false)));

        System.out.println("t1 = " + t1 + "ms, t2 = " + t2 + "ms, t3 = " + t3 + "ms, t4 = " + t4 + " \nbench = " + Bench.dump("global"));

    }

    private File fetch(String locationUri) throws IOException {
        final File targetDirectory = tmp.newFolder();
        final Rsync rsync = new Rsync(locationUri, targetDirectory.getPath());
        rsync.addOptions("--update", "--times", "--copy-links", "--recursive", "--delete");
        rsync.execute();
        return targetDirectory;
    }


    private void traverse(File targetDirectory, String locationUri, boolean createObjects) throws IOException {
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger broken = new AtomicInteger(0);
        AtomicInteger right = new AtomicInteger(0);
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

                if (createObjects) {
                    final byte[] content = Files.readAllBytes(file);
                    final byte[] sha256 = Sha256.hash(content);

                    final String key = Hex.format(sha256);
                    final String location = currentLocation.toString();

                    final Either<ValidationResult, Pair<String, RpkiObject>> rpkiObject = Bench.mark(
                        "RpkiObjectUtils.createRpkiObject", () -> RpkiObjectUtils.createRpkiObject(file.getFileName().toString(), content));
                    counter.incrementAndGet();
                    if (rpkiObject.isRight()) {
                        right.incrementAndGet();
                    } else {
                        broken.incrementAndGet();
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Processed " + counter.get() + " objects");
    }
}