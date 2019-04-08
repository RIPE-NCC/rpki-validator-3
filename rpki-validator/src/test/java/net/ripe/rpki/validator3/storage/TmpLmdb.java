package net.ripe.rpki.validator3.storage;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.Env.create;

@Profile("test")
@Component
@Slf4j
public class TmpLmdb extends Lmdb {

    @Getter
    private Env<ByteBuffer> env;

    private final Path mdb;

    public TmpLmdb() throws IOException {
        mdb = Files.createTempDirectory("test-rpki-validator-");
//        mdb = Paths.get("/tmp/test-rpki-validator/");
        File file = mdb.toFile();
        if (!file.exists()) {
            file.mkdirs();
        }
        env = open();
    }

    private Env<ByteBuffer> open() {
        return create()
                .setMapSize(1024 * 1024 * 1024L)
                .setMaxDbs(100)
                .open(mdb.toFile());
    }

    @PreDestroy
    public void close() {
        getEnv().close();
    }
}
