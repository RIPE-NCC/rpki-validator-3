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
package net.ripe.rpki.validator3.api.slurm;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmExt;
import net.ripe.rpki.validator3.storage.encoding.GsonCoder;
import org.apache.tomcat.util.http.fileupload.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Always store SLURM spec as a file.
 */
@Slf4j
public class SlurmStore {

    private final String slurmFileName;

    private final Gson gson;

    private SlurmExt slurmExt;

    @Getter
    private final AtomicLong idSeq = new AtomicLong(0);

    public SlurmStore(String path) {
        final File slurmFile = new File(path, "slurm.json");
        this.slurmFileName = slurmFile.getAbsolutePath();
        this.gson = GsonCoder.getPrettyGson();
        if (slurmFile.exists()) {
            log.info("SLURM file {} already exists, reading it", this.slurmFileName);
            setIdSequenceStartValue(read());
        } else {
            if (!new File(path).isDirectory()) {
                throw new RuntimeException("Cannot write to the SLURM file, probably the path " + path + " doesn't exist");
            } else {
                log.info("SLURM file {} doesn't exist, creating a new one", slurmFile.getAbsolutePath());
                save(new SlurmExt());
                setIdSequenceStartValue(read());
            }
        }
    }

    public synchronized void save(SlurmExt slurm) {
        slurmExt = slurm.copy();
        final String tmp = slurmFileName + ".tmp";
        try {
            byte[] bytes = gson.toJson(slurmExt.toSlurm()).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(bytes);
                new File(slurmFileName).delete();
                Files.move(Paths.get(tmp), Paths.get(slurmFileName));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            new File(tmp).delete();
        }
    }

    public synchronized SlurmExt read() {
        if (slurmExt != null) {
            return slurmExt;
        }
        try {
            final Slurm slurm = gson.fromJson(new FileReader(new File(slurmFileName)), Slurm.class);
            slurmExt = SlurmExt.fromSlurm(slurm, idSeq);
            return slurmExt;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized <T> T updateWith(Function<SlurmExt, T> f) {
        final SlurmExt s = read();
        T t = f.apply(s);
        save(s);
        return t;
    }

    public synchronized void updateWith(Consumer<SlurmExt> c) {
        final SlurmExt s = read();
        c.accept(s);
        save(s);
    }

    private void setIdSequenceStartValue(SlurmExt s) {
        long maxId = Stream.of(
                s.getBgpsecAssertions(),
                s.getBgpsecFilters(),
                s.getPrefixAssertions(),
                s.getPrefixFilters())
                .map(s1 -> s1.keySet().stream()
                        .max(Long::compareTo)
                        .orElse(0L))
                .max(Long::compareTo)
                .orElse(0L);
        idSeq.set(maxId + 1);
    }

    public long nextId() {
        return idSeq.getAndIncrement();
    }

    synchronized void importSlurm(Slurm slurm) {
        save(SlurmExt.fromSlurm(slurm, idSeq));
    }

    void writeTo(OutputStream out) throws IOException {
        IOUtils.copy(new FileInputStream(new File(slurmFileName)), out);
    }
}
