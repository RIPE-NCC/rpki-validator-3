package net.ripe.rpki.validator3.storage.xodus;

import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import net.ripe.rpki.validator3.util.Time;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jetbrains.exodus.bindings.StringBinding.stringToEntry;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES;
import static org.junit.Assert.assertEquals;


public class XodusTest {

    @Test

    public void testXodusSpeed() throws IOException {

        //Create environment or open existing one
        final Environment env = Environments.newInstance("data");
        env.clear();

        //Create or open existing store in environment
        final Store store = env.computeInTransaction(txn -> env.openStore("MyStore", WITHOUT_DUPLICATES, txn));

        final long NREPEAT = 1000_000;


        List<ByteIterable> keys = new ArrayList<>();

        Long t = Time.timed(() -> {
            env.executeInTransaction(txn -> {

                        for (int i = 0; i < NREPEAT; i++) {
                            final ByteIterable key = randomUUIDByteIterable();
                            store.put(txn, key, stringToEntry("blabla" + key.toString()));
                            keys.add(key);
                        }
                        txn.commit();
                    }
            );
        });

        System.out.printf("Xodus storing %d uuids took %d ", NREPEAT, t);
        long counts = env.computeInTransaction(store::count);

        assertEquals(NREPEAT, counts);

        env.executeInTransaction(txn -> {
                    for (ByteIterable key : keys) {
                            assertEquals(stringToEntry("blabla" + key.toString()), store.get(txn, key));
                    }
                });

        // Close environment when we are done
        env.close();

    }

    private static ByteIterable randomUUIDByteIterable() {
        UUID uuid = makeUuid();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());

        return new ArrayByteIterable(bb.array());
    }

    private static UUID makeUuid() {
        return UUID.randomUUID();
    }

}
