package net.oneki.mtac.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ByteUtil {
   public static byte[] decodeFromBase64(byte[] arr) {
        return Base64.getDecoder().decode(arr);
    }
    
    public static byte[] encodeToBase64(byte[] arr) {
        return Base64.getEncoder().encode(arr);
    }


    public static byte[] decodeFromGZIP(byte[] arr) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(arr);
        GZIPInputStream gzip = new GZIPInputStream(bais);
        return gzip.readAllBytes();
    }

    public static byte[] encodeToGZIP(byte[] arr) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(baos);
        gzip.write(arr);
        gzip.finish();
        return baos.toByteArray();
    } 
}
