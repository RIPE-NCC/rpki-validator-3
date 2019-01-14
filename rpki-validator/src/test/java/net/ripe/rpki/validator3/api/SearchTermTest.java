package net.ripe.rpki.validator3.api;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.domain.ValidatedRpkiObjects.RoaPrefix;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SearchTermTest {

    final RoaPrefix prefixTest = RoaPrefix.of(null, null, IpRange.parse("10.0.0.0/8"), 32, 32, null);
    final RoaPrefix asnTest = RoaPrefix.of(null,  Asn.parse("3642"), null, 32, 32, null);


    @Test
    public void should_accept_matching_asn(){
        SearchTerm validASN = new SearchTerm("3642");
        validASN.test(asnTest);
        assertThat(validASN.test(asnTest)).isTrue();
    }

    @Test
    public void should_accept_matching_prefix(){
        SearchTerm searchPrefix = new SearchTerm("10.0.0.0");
        searchPrefix.test(prefixTest);
        assertThat(searchPrefix.test(prefixTest)).isTrue();
    }

    @Test
    public void should_reject_mismatched_asn(){
        SearchTerm validASN = new SearchTerm("1111");
        validASN.test(asnTest);
        assertThat(validASN.test(asnTest)).isFalse();
    }

    @Test
    public void should_reject_non_overlapping_prefix(){
        SearchTerm searchPrefix = new SearchTerm("11.0.0.0");
        searchPrefix.test(prefixTest);
        assertThat(searchPrefix.test(prefixTest)).isFalse();
    }

}