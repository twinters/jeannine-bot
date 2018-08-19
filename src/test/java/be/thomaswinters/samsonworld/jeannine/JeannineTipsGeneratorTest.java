package be.thomaswinters.samsonworld.jeannine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JeannineTipsGeneratorTest {

    private JeannineTipsGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        generator = new JeannineTipsGenerator();
    }

    @Test
    void test_wikihow_tips_searching_basic() {
        List<String> tips = generator.searchForTips("baan");
        System.out.println(tips);
        assertFalse(tips.isEmpty());
    }

}