package com.mesilat.zabbix;

public class ArrayUtil {

    public static String[] merge(String[] a1, String[] a2) {
        if (a1 == null) {
            return a2;
        } else if (a2 == null) {
            return a1;
        } else {
            String[] a = new String[a1.length + a2.length];
            int i = 0;
            for (String s : a1) {
                a[i++] = s;
            }
            for (String s : a2) {
                a[i++] = s;
            }
            return a;
        }
    }

    public static byte[] merge(byte[] a1, byte[] a2) {
        if (a1 == null) {
            return a2;
        } else if (a2 == null) {
            return a1;
        } else {
            byte[] a = new byte[a1.length + a2.length];
            int i = 0;
            for (byte s : a1) {
                a[i++] = s;
            }
            for (byte s : a2) {
                a[i++] = s;
            }
            return a;
        }
    }

    public static char[] makeChars(char ch, int n) {
        if (n <= 0) {
            return new char[0];
        } else {
            char[] result = new char[n];
            for (int i = 0; i < n; i++) {
                result[i] = ch;
            }
            return result;
        }
    }
}
