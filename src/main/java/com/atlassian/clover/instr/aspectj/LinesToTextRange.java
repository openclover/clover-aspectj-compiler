package com.atlassian.clover.instr.aspectj;

import java.util.ArrayList;

public class LinesToTextRange {
    private final ArrayList<Integer> linesEndIndex;

    public LinesToTextRange(String input) {
        linesEndIndex = new ArrayList<Integer>();

        String[] lines = input.split("[\n\r]");
        int endIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            endIndex += lines[i].length() + (i < lines.length - 1 ? 1 : 0);
            linesEndIndex.add(endIndex);
        }
    }

    /**
     * @param lineNumber counted from 0
     * @return TextRange
     */
    public TextRange getTextRangeForLine(int lineNumber) {
        if (lineNumber >= 0 && lineNumber < linesEndIndex.size()) {
            int min = lineNumber == 0 ? 0 : linesEndIndex.get(lineNumber - 1) + 1;
            int max = linesEndIndex.get(lineNumber);
            return new TextRange(min, max);
        } else {
            return new TextRange(0, 0);
        }
    }
}
