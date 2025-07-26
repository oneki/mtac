package net.oneki.mtac.framework.util.security;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PasswordUtil {
  private final PasswordEncoder passwordEncoder;
  private final StringEncryptor stringEncryptor;
  
  public String hash(String plainText) {
    return passwordEncoder.encode(plainText);
  }

  public String encrypt(String plainText) {
    return stringEncryptor.encrypt(plainText);
  }

  public String decrypt(String encryptedText) {
    return stringEncryptor.decrypt(encryptedText);
  }

  public boolean matches(String plainText, String hashedPassword) {
    return passwordEncoder.matches(plainText, hashedPassword);
  }

}
