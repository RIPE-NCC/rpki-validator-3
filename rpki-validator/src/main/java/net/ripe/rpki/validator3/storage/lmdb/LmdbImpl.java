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
package net.ripe.rpki.validator3.storage.lmdb;

import lombok.extern.slf4j.Slf4j;
import org.lmdbjava.Env;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lmdbjava.Env.create;

@Profile("!test")
@Component
@Slf4j
public class LmdbImpl extends Lmdb {

    private final long dbSizeInMb;
    private final String lmdbPath;

    private Env<ByteBuffer> env;

    // this is to guarantee that 'env' is opened and closed by the same thread
    private ExecutorService oneThread = Executors.newSingleThreadExecutor();

    public LmdbImpl(
            @Value("${rpki.validator.data.path}") String lmdbPath,
            @Value("${rpki.validator.lmdb.size.mb:8192}") long dbSizeInMb) {
        this.dbSizeInMb = dbSizeInMb;
        this.lmdbPath = lmdbPath;
    }

    @PostConstruct
    public void initLmdb() {
        try {
            log.info("Creating LMDB environment at {}", lmdbPath);
            oneThread.submit(() -> {
                env = create()
                        .setMapSize(dbSizeInMb * 1024L * 1024L)
                        .setMaxDbs(100)
                        .open(new File(lmdbPath));
            }).get();

            Runtime.getRuntime().addShutdownHook(new Thread(this::waitForAllTxToFinishAndClose));
        } catch (Exception e) {
            log.error("Couldn't open LMDB", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public synchronized void waitForAllTxToFinishAndClose() {
        if (!env.isClosed()) {
            log.info("Preparing to close the LMDB environment, waiting for all transactions to finish...");
            while (!getTxs().isEmpty()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignore) {
                }
            }
            close();
        }
    }

    private synchronized void close() {
        try {
            oneThread.submit(() -> {
                if (!env.isClosed()) {
                    try {
                        log.info("Closing LMDB environment at {}", lmdbPath);
                        env.close();
                    } catch (Throwable e) {
                        log.error("Couldn't close LMDB", e);
                    }
                }
            }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Env<ByteBuffer> getEnv() {
        return env;
    }
}
