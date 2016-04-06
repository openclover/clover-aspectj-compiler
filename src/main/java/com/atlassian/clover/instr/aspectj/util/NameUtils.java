package com.atlassian.clover.instr.aspectj.util;

import com.atlassian.clover.api.registry.PackageInfo;

public class NameUtils {

    public static char[][] stringsToCharArrays(String... strings) {
        final int size = strings.length;
        final char[][] charArrays = new char[size][];
        for (int i = 0; i < size; i++) {
            charArrays[i] = strings[i].toCharArray();
        }
        return charArrays;
    }

    public static String packageNameToString(final char[][] currentPackageName) {
        final String name = qualifiedNameToString(currentPackageName);
        return name.isEmpty() ? PackageInfo.DEFAULT_PACKAGE_NAME : name;
    }

    public static String qualifiedNameToString(final char[][] qualifiedName) {
        String name = "";
        for (int i = 0; i < qualifiedName.length; i++) {
            name += String.valueOf(qualifiedName[i]);
            if (i < qualifiedName.length - 1) {
                name += ".";
            }
        }
        return name;
    }
}
