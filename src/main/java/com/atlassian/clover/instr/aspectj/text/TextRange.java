package com.atlassian.clover.instr.aspectj.text;

public class TextRange  {
    private final int myStartOffset;
    private final int myEndOffset;

    public TextRange(int startOffset, int endOffset) {
        this.myStartOffset = startOffset;
        this.myEndOffset = endOffset;
    }

    public final int getStartOffset() {
        return this.myStartOffset;
    }

    public final int getEndOffset() {
        return this.myEndOffset;
    }
}
