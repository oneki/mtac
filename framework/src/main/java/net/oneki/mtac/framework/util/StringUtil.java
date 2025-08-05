package net.oneki.mtac.framework.util;

public class StringUtil {
  public static String randomBase64String(int length) {
    byte[] randomBytes = new byte[length];
    java.security.SecureRandom secureRandom = new java.security.SecureRandom();
    secureRandom.nextBytes(randomBytes);
    return java.util.Base64.getEncoder().encodeToString(randomBytes);
  }
}
