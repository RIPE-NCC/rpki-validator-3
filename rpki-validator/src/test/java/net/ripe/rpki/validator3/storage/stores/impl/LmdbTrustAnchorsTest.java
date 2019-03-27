package net.ripe.rpki.validator3.storage.stores.impl;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.TestObjects;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.lmdb.Tx;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@IntegrationTest
@Slf4j
public class LmdbTrustAnchorsTest extends GenericStorageTest {

    @Test
    public void should_find_trust_anchors_by_case_insensitive_name() {
        final TrustAnchor trustAnchor = TestObjects.newTrustAnchor();
        Tx.use(getLmdb().writeTx(), tx -> getTrustAnchorStore().add(tx, trustAnchor));

        List<TrustAnchor> byName = Tx.rwith(getLmdb().readTx(), tx -> getTrustAnchorStore().findByName(tx, "Trust Anchor"));
        assertThat(byName).isNotEmpty();
        assertThat(byName.get(0)).isEqualTo(trustAnchor);
    }

}