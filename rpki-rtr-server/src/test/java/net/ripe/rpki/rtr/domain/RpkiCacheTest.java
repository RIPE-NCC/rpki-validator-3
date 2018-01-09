package net.ripe.rpki.rtr.domain;

import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.rtr.domain.pdus.Pdu;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class RpkiCacheTest {

    private RpkiCache subject = new RpkiCache();

    @Test
    public void should_increase_serial_when_valid_pdus_change() {
        assertThat(subject.getSerialNumber()).isEqualTo(0);
        subject.updateValidatedPdus(Collections.singleton(Pdu.prefix(Asn.parse("AS3333"), IpRange.parse("127.0.0.0/8"), 14)));
        assertThat(subject.getSerialNumber()).isEqualTo(1);
    }

}