package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.util.FileUtils;
import com.atlassian.clover.util.collections.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.ArrayList;

/**
 * Converts character position in a file to line:column position.
 */
public class CharToLineColMapper {

    private final ArrayList<Integer> linesEndings = new ArrayList<Integer>(1000);

    public CharToLineColMapper(File sourceFile) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(sourceFile);
            parseSourceFile(inputStream);
        } catch (IOException ex) {
            System.out.println("Failed to read " + sourceFile);
        } finally {
            FileUtils.close(inputStream);
        }
    }

    public CharToLineColMapper(String sourceFile) {
        try {
            parseSourceFile(new StringBufferInputStream(sourceFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Pair<Integer, Integer> getLineColFor(int characterPosition) {
        int previousEnd = -1;
        int currentEnd = 0;
        for (int lineNum = 0; lineNum < linesEndings.size(); lineNum++) {
            currentEnd = linesEndings.get(lineNum);
            if (characterPosition > previousEnd && characterPosition <= currentEnd) {
                // found line
                int colNum = characterPosition - previousEnd;
                return Pair.of(lineNum + 1, colNum); // we count line/col from 1
            }
            previousEnd = currentEnd;
        }

        return Pair.of(0, 0);
    }

    protected void parseSourceFile(InputStream inputStream) throws IOException {
        int index = 0;
        int currentChar;
        int previousChar = -1;
        while ((currentChar = inputStream.read()) != -1) {
            if (currentChar == '\n') {
                // windows/mac
                linesEndings.add(index);
            } else if (previousChar == '\r'/* && currentChar != '\n'*/) {
                // linux
                linesEndings.add(index - 1);
            }
            previousChar = currentChar;
            index++;
        }
    }
}
