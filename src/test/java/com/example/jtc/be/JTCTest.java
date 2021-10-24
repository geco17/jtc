package com.example.jtc.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class JTCTest {

    private final JTC jtc = new JTC(new ObjectMapper());

    @Test
    public void test1() {
        jtc.convert(getClass().getResourceAsStream("/1.json"));
    }

    @Test
    public void test2() {
        jtc.convert(getClass().getResourceAsStream("/2.json"));
    }

    @Test
    public void test3() {
        jtc.convert(getClass().getResourceAsStream("/3.json"));
    }

    @Test
    public void test4() {
        jtc.convert(getClass().getResourceAsStream("/4.json"));
    }

    @Test
    public void test5() {
        jtc.convert(getClass().getResourceAsStream("/5.json"));
    }

}
