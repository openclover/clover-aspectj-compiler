package com.atlassian.clover.instr.aspectj.text;

import com.atlassian.clover.instr.aspectj.text.LinesToTextRange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LinesToTextRangeTest {

    @Test
    public void testGetTextRangeForLine() {
        LinesToTextRange ranger = new LinesToTextRange(
                "abc\n"
                + "defghi\r"
                + "\n"
                + "a\r"
                + "xyz");

        assertEquals(0, ranger.getTextRangeForLine(0).getStartOffset());
        assertEquals(4, ranger.getTextRangeForLine(0).getEndOffset());

        assertEquals(5, ranger.getTextRangeForLine(1).getStartOffset());
        assertEquals(11, ranger.getTextRangeForLine(1).getEndOffset());

        assertEquals(12, ranger.getTextRangeForLine(2).getStartOffset());
        assertEquals(12, ranger.getTextRangeForLine(2).getEndOffset());

        assertEquals(13, ranger.getTextRangeForLine(3).getStartOffset());
        assertEquals(14, ranger.getTextRangeForLine(3).getEndOffset());

        assertEquals(15, ranger.getTextRangeForLine(4).getStartOffset());
        assertEquals(17, ranger.getTextRangeForLine(4).getEndOffset());

        assertEquals(0, ranger.getTextRangeForLine(5).getEndOffset()); // out of bounds
    }
}
