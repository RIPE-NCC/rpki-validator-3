package net.ripe.rpki.validator3.storage.xodus;

import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class XodusImpl extends Xodus {

    private final String path;
    private ExecutorService oneThread = Executors.newSingleThreadExecutor();

    private Environment env;

    public XodusImpl(
            @Value("${rpki.validator.data.path}") String path) {
        this.path = path;
    }

    @PostConstruct
    public void initLmdb() {
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
            log.info("Preparing to close the Xodus environment, waiting for all transactions to finish...");
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
                if (env.isOpen()) {
                    try {
                        log.info("Closing Xodus environment at {}", path);
                        env.close();
                    } catch (Throwable e) {
                        log.error("Couldn't close Xodus", e);
                    }
                }
            }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Environment getEnv() {
        return env;
    }
}
