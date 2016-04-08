package com.mesilat.zabbix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Base64;

public class PasswordEncryption {

    private static final String KEY_FACTORY = "PBKDF2WithHmacSHA1";
    private static final String CIPHER = "AES/CBC/PKCS5Padding";
    private static final String ALGORITHM = "AES";

    public static byte[] encrypt(byte[] text, String password) throws
            NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException {
        return encrypt(text, password, "eH9!".getBytes());
    }

    public static byte[] encrypt(byte[] text, String password, byte[] salt) throws
            NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

        /* Encrypt the message. */
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        AlgorithmParameters params = cipher.getParameters();
        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();

        return ArrayUtil.merge(iv, cipher.doFinal(text));
    }

    public static byte[] decrypt(byte[] enc, String password) throws
            NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
        return decrypt(enc, password, "eH9!".getBytes());
    }

    public static byte[] decrypt(byte[] enc, String password, byte[] salt) throws
            NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidKeyException, InvalidParameterSpecException, IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_FACTORY);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

        /* Decrypt the message. */
        Cipher cipher = Cipher.getInstance(CIPHER);
        byte[] iv = Arrays.copyOfRange(enc, 0, 16);
        cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));

        return cipher.doFinal(Arrays.copyOfRange(enc, 16, enc.length));
    }

    public static String scramble(String text) throws Exception {
        return new String(Base64.encode(encrypt(text.getBytes("UTF8"), KEY_FACTORY + CIPHER + ALGORITHM)));
    }

    public static String unscramble(String text) throws Exception {
        return new String(decrypt(Base64.decode(text), KEY_FACTORY + CIPHER + ALGORITHM), "UTF8");
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        BufferedOutputStream bout = new BufferedOutputStream(out);

        while (true) {
            int datum = bin.read();
            if (datum == -1) {
                break;
            }
            bout.write(datum);
        }
        bout.flush();
        try {
            in.close();
        } catch (IOException ignore) {
        }
        try {
            out.close();
        } catch (IOException ignore) {
        }
    }

    public static byte[] readFile(File file) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        copy(new FileInputStream(file), buf);
        return buf.toByteArray();
    }

    public static void writeFile(byte[] data, File file) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        copy(in, new FileOutputStream(file));
    }
}
