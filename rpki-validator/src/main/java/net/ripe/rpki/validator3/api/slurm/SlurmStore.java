package net.ripe.rpki.validator3.api.slurm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmExt;
import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.encoding.GsonCoder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Always store SLURM spec as a file.
 */
@Component
@Slf4j
public class SlurmStore {

    private final String path;

    private final GsonCoder<SlurmExt> coder;

    private SlurmExt slurmExt;

    @Getter
    private AtomicLong idSeq;

    @Autowired
    public SlurmStore(@Value("rpki.validator.slurm.path") String path) {
        this.path = path;
        coder = new GsonCoder<>(SlurmExt.class);
        final File file = new File(path);
        if (file.exists()) {
            log.info("SLURM file {} already exists, reading it", path);
            setIdSequenceStartValue(read());
        } else {
            if (!file.canWrite()) {
                log.error("SLURM file doesn't exist, creating a new one");
                throw new RuntimeException("Cannot write to the SLURM file, probably the path " + path + " doesn't exist");
            } else {
                log.info("SLURM file {} doesn't exist, creating a new one", path);
                save(new SlurmExt());
                setIdSequenceStartValue(read());
            }
        }
    }

    // TODO Add
    public synchronized void save(SlurmExt slurm) {
        slurmExt = slurm.copy();
        final String tmp = path + ".tmp";
        try {
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(Bytes.toBytes(coder.toBytes(slurm)));
                Files.move(Paths.get(tmp), Paths.get(path));
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
            byte[] slurmContent = Files.readAllBytes(new File(path).toPath());
            slurmExt = coder.fromBytes(slurmContent);
            setIdSequenceStartValue(slurmExt);
            return slurmExt;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized <T> T update(Function<SlurmExt, T> f) {
        final SlurmExt s = read();
        T t = f.apply(s);
        save(s);
        return t;
    }

    public synchronized void update(Consumer<SlurmExt> c) {
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
                .map(s1 -> s1.stream()
                        .map(Pair::getLeft)
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
}
