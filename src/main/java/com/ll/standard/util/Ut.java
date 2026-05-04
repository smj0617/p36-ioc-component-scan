package com.ll.standard.util;

public class Ut {
    public static class str {
        public static String lcfirst(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }

            char firstChar = str.charAt(0);

            if (Character.isLowerCase(firstChar)) {
                return str;
            }

            return Character.toLowerCase(firstChar) + str.substring(1);
        }
    }
}
