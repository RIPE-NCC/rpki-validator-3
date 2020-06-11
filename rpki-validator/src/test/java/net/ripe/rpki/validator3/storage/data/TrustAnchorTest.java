package net.ripe.rpki.validator3.storage.data;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class TrustAnchorTest {
    @Test
    public void getLocationsByPreference_prefers_https_over_rsync() {
        TrustAnchor ta = new TrustAnchor();
        ta.setLocations(Lists.newArrayList("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer", "https://rpki.ripe.net/ta/ripe-ncc-ta.cer"));

        assertThat(ta.getLocationsByPreference()).containsExactly(
                URI.create("https://rpki.ripe.net/ta/ripe-ncc-ta.cer"),
                URI.create("rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer")
        );
    }

    @Test
    public void getLocationsByPreference_handles_empty() {
        TrustAnchor ta = new TrustAnchor();
        ta.setLocations(Lists.newArrayList());

        assertThat(ta.getLocationsByPreference()).isEmpty();
    }
}
