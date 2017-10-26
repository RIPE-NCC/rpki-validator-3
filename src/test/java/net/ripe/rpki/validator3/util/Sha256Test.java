package net.ripe.rpki.validator3.util;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class Sha256Test {
    @Test
    public void parse() throws Exception {
        check(null);
        check("");
        check("22");
        check("6AB8");
        check("226AB8CD3C887A6EBDDDF317F2FAFC9CF3EFC5D43A86347AC0FEFFE4DC0F607E");
    }

    private static void check(String hex) throws IOException {
        assertEquals(hex, Sha256.format(Sha256.parse(hex)));
    }

}