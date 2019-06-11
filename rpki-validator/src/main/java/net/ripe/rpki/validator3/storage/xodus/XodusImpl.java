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
package net.ripe.rpki.validator3.storage.xodus;

import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.EnvironmentImpl;
import jetbrains.exodus.env.Environments;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.MultIxMap;
import net.ripe.rpki.validator3.storage.encoding.Coder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Profile("!test")
@Component
@Slf4j
@Primary
public class XodusImpl extends Xodus {

    private final String path;
    private ExecutorService oneThread = Executors.newSingleThreadExecutor();

    private Environment env;

    public XodusImpl(@Value("${rpki.validator.data.path}") String path) {
        this.path = path;
    }

    @PostConstruct
    public void initXodus() {
        try {
            log.info("Creating Xodus environment at {}", path);
            oneThread.submit(() -> {
                final EnvironmentConfig config = new EnvironmentConfig()
                        .setLogDurableWrite(true)
                        .setEnvGatherStatistics(true)
                        .setLogCacheUseNio(true);

                env = Environments.newInstance(path, config);
            }).get();

            Runtime.getRuntime().addShutdownHook(new Thread(this::waitForAllTxToFinishAndClose));
        } catch (Exception e) {
            log.error("Couldn't open Xodus", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public synchronized void waitForAllTxToFinishAndClose() {
        if (env.isOpen()) {
            log.info("Stuck in transaction " + ((EnvironmentImpl)env).getStuckTransactionCount());
            env.clear();
            env.close();
        }
    }

    @Override
    protected Environment getEnv() {
        return env;
    }
}
