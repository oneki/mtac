package net.oneki.mtac.framework.util.security;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.oneki.mtac.framework.util.StringUtil;

@RequiredArgsConstructor
@Slf4j
public class PasswordUtil {
  private static PasswordEncoder passwordEncoder;
  private static StringEncryptor stringEncryptor;

  public static String hash(String plainText) {
    return passwordEncoder.encode(plainText);
  }

  public static String encrypt(String plainText) {
    return stringEncryptor.encrypt(plainText);
  }

  public static String decrypt(String encryptedText) {
    return stringEncryptor.decrypt(encryptedText);
  }

  public static boolean matches(String plainText, String hashedPassword) {
    return passwordEncoder.matches(plainText, hashedPassword);
  }

  public static String randomPassword() {
    return StringUtil.randomBase64String(32);
  }

  public static void init(PasswordEncoder passwordEncoder, StringEncryptor stringEncryptor) {
    log.info("Initializing PasswordUtil with provided PasswordEncoder and StringEncryptor");
    PasswordUtil.passwordEncoder = passwordEncoder;
    PasswordUtil.stringEncryptor = stringEncryptor;
  }

}
