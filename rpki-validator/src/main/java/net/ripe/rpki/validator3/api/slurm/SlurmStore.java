package net.ripe.rpki.validator3.api.slurm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.api.slurm.dtos.Slurm;
import net.ripe.rpki.validator3.api.slurm.dtos.SlurmExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
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
@Component
@Slf4j
public class SlurmStore {

    private final String slurmFileName;

    private final Gson gson;

    private SlurmExt slurmExt;

    @Getter
    private final AtomicLong idSeq = new AtomicLong(0);

    @Autowired
    public SlurmStore(@Value("${rpki.validator.data.path}") String path) {
        final File slurmFile = new File(path, "slurm.json");
        this.slurmFileName = slurmFile.getAbsolutePath();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        if (slurmFile.exists()) {
            log.info("SLURM file {} already exists, reading it", this.slurmFileName);
            setIdSequenceStartValue(read());
        } else {
            if (!new File(path).isDirectory()) {
                log.error("SLURM file doesn't exist, creating a new one");
                throw new RuntimeException("Cannot write to the SLURM file, probably the path " + path + " doesn't exist");
            } else {
                log.info("SLURM file {} doesn't exist, creating a new one", path);
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
}
