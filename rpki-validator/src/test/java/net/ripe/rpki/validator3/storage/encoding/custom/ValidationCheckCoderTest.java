package net.ripe.rpki.validator3.storage.encoding.custom;

import net.ripe.rpki.commons.validation.ValidationStatus;
import net.ripe.rpki.validator3.domain.ErrorCodes;
import net.ripe.rpki.validator3.storage.data.validation.ValidationCheck;
import net.ripe.rpki.validator3.storage.encoding.custom.validation.ValidationCheckCoder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationCheckCoderTest {
    @Test
    public void testEncode_with_null_param() {
        ValidationCheck check = new ValidationCheck(
                "rsync://rpki.example.org/ta/ta.cer",
                new net.ripe.rpki.commons.validation.ValidationCheck(ValidationStatus.WARNING, ErrorCodes.TRUST_ANCHOR_FETCH, "first param", null));

        ValidationCheckCoder coder = new ValidationCheckCoder();

        assertThat(coder.fromBytes(coder.toBytes(check))).isEqualTo(check);
    }
}
