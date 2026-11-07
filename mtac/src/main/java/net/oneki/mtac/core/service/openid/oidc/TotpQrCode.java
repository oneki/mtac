package net.oneki.mtac.core.service.openid.oidc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * QR code a user scans with an authenticator application to enrol the TOTP
 * secret, produced on the server so that the page needs no script.
 */
public final class TotpQrCode {
  private TotpQrCode() {
  }

  /** otpauth URI understood by the authenticator applications. */
  public static String otpAuthUri(String issuerName, String account, String secret) {
    var issuer = encode(issuerName);
    return "otpauth://totp/" + issuer + ":" + encode(account) + "?secret=" + secret + "&issuer=" + issuer;
  }

  /** The QR code as an SVG image in a data URI, usable as the src of an img. */
  public static String dataUri(String content) {
    try {
      var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 2));
      var path = new StringBuilder();
      for (var y = 0; y < matrix.getHeight(); y++) {
        for (var x = 0; x < matrix.getWidth(); x++) {
          if (matrix.get(x, y)) {
            path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
          }
        }
      }
      var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " "
          + matrix.getHeight() + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
          + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
      return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    } catch (WriterException e) {
      throw new IllegalStateException("Unable to generate the QR code", e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
