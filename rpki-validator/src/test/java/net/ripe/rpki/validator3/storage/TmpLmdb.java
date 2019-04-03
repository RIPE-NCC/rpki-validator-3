package net.ripe.rpki.validator3.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lmdbjava.Env;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lmdbjava.Env.create;

@Profile("test")
@Component
@Slf4j
public class TmpLmdb extends Lmdb {

    @Getter
    private final Env<ByteBuffer> env;

    public TmpLmdb() throws IOException {
        Path mdb = Files.createTempDirectory("test-rpki-validator-");
        env = create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(mdb.toFile());
    }

    @PreDestroy
    public void close() {
        env.close();
    }
}
