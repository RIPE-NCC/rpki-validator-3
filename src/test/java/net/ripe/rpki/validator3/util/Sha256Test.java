package net.ripe.rpki.validator3.util;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class Sha256Test {
    @Property
    public void formatAndParse(byte[] bytes) throws Exception {
        assertTrue(Arrays.equals(bytes, Hex.parse(Hex.format(bytes))));
    }
}