package com.fizzed.buildx.internal;

public class Utils {

    static public String stringify(Object obj, String defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        return obj.toString();
    }

    static public String stringifyLowerCase(Object obj, String defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        return obj.toString().toLowerCase();
    }

}