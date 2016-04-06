package com.atlassian.clover.instr.aspectj.text;

import com.atlassian.clover.instr.aspectj.text.CharToLineColMapper;
import com.atlassian.clover.util.collections.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test for {@link }
 */
public class CharToLineColMapperTest {

    @Test
    public void testParseSourceFile() throws Exception {
        String sourceFile = "abc\r"     // 0..3
                + "def\n"               // 4..7
                + "ghi\r\n"             // 8..12
                + "jkl\r"               // 16
                + "\r"                  // 17
                + "xyz\n"               // 18..21
                + "\n"                  // 22
                + "\n";                 // 23
        CharToLineColMapper mapper = new CharToLineColMapper(sourceFile);

        assertEquals(Pair.of(1, 1), mapper.getLineColFor(0));
        assertEquals(Pair.of(1, 4), mapper.getLineColFor(3));

        assertEquals(Pair.of(2, 1), mapper.getLineColFor(4));
        assertEquals(Pair.of(2, 4), mapper.getLineColFor(7));

        assertEquals(Pair.of(3, 1), mapper.getLineColFor(8));
        assertEquals(Pair.of(3, 4), mapper.getLineColFor(11));
        assertEquals(Pair.of(3, 5), mapper.getLineColFor(12));

        assertEquals(Pair.of(4, 1), mapper.getLineColFor(13));
    }
}