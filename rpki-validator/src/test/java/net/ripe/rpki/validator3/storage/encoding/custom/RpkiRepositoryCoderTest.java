package net.ripe.rpki.validator3.storage.encoding.custom;

import net.ripe.rpki.validator3.storage.data.Key;
import net.ripe.rpki.validator3.storage.data.Ref;
import net.ripe.rpki.validator3.storage.data.RpkiRepository;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;

import static org.junit.Assert.*;

public class RpkiRepositoryCoderTest {

    @Test
    public void testSaveRead() {
        Ref<TrustAnchor> trustAnchorRef = Ref.unsafe("bla", Key.of(123L));
        RpkiRepository rpkiRepository = new RpkiRepository(trustAnchorRef, "some-uri", RpkiRepository.Type.RRDP);
        Ref<RpkiRepository> parentRepoRef = Ref.unsafe("foo", Key.of(987654321L));
        rpkiRepository.setParentRepository(parentRepoRef);
        rpkiRepository.setLastDownloadedAt(Instant.now());
        rpkiRepository.setRrdpSerial(new BigInteger("2133553334897396402696204629648763485348763845"));
        rpkiRepository.setRrdpSessionId("sfjbkskbsfkbjsfkjbs");

        RpkiRepositoryCoder coder = new RpkiRepositoryCoder();
        RpkiRepository rpkiRepository1 = coder.fromBytes(coder.toBytes(rpkiRepository));

        assertEquals(rpkiRepository, rpkiRepository1);
    }
}