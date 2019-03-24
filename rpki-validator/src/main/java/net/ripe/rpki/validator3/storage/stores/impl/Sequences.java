package net.ripe.rpki.validator3.storage.stores.impl;

import net.ripe.rpki.validator3.storage.Bytes;
import net.ripe.rpki.validator3.storage.Coder;
import net.ripe.rpki.validator3.storage.Lmdb;
import net.ripe.rpki.validator3.storage.lmdb.IxMap;
import net.ripe.rpki.validator3.storage.lmdb.Key;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Optional;

@Component
public class Sequences {

    private final String SEQUENCES = "sequences";
    private final IxMap<BigInteger> ixMap;

    @Autowired
    public Sequences(Lmdb lmdb) {
        this.ixMap = new IxMap<>(
                lmdb.getEnv(),
                SEQUENCES,
                new Coder<BigInteger>() {
                    @Override
                    public ByteBuffer toBytes(BigInteger bigInteger) {
                        return Bytes.toDirectBuffer(bigInteger.toByteArray());
                    }

                    @Override
                    public BigInteger fromBytes(ByteBuffer bb) {
                        return new BigInteger(Bytes.toBytes(bb));
                    }
                });
    }


    public BigInteger next(Tx.Write<ByteBuffer> tx, String name) {
        final Key key = Key.of(name);
        final Optional<BigInteger> seqValue = ixMap.get(tx, key);
        if (seqValue.isPresent()) {
            final BigInteger nextValue = seqValue.get().add(BigInteger.ONE);
            ixMap.put(tx, key, nextValue);
            return nextValue;
        }
        ixMap.put(tx, key, BigInteger.ONE);
        return BigInteger.ONE;
    }

}
