package net.oneki.mtac.model.core.util.exception;

public enum ErrorCode {
  SITE_NOT_AVAILABLE("The site is currently disconnected from Fuzz Cloud.");

  private String message;
  ErrorCode(String message) {
    this.message = message;
  }

  public String message() {
    return this.message;
  }

  public String message(String... args) {
    var result = message();
    if (args == null) return result;
    var i = 1;
    for (var arg : args) {
      result = result.replace("%"+i, arg);
      i++;
    }
    return result;
  }
}
