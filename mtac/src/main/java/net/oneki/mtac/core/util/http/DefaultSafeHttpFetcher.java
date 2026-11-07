package net.oneki.mtac.core.util.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.springframework.stereotype.Component;

/**
 * {@link SafeHttpFetcher} over a plain TLS socket and a minimal HTTP/1.1 GET.
 *
 * <p>
 * Why not {@code java.net.http.HttpClient}: it resolves the host itself and
 * offers no per-client resolver, so the address it connects to could differ from
 * the one that was checked (DNS rebinding: a first answer with a public address,
 * a second one with 169.254.169.254). Here the host is resolved once, every
 * address of the answer must be public, and the socket connects to one of those
 * checked addresses. TLS still verifies the certificate against the host name
 * (SNI + HTTPS endpoint identification), so connecting to an IP does not weaken
 * the server authentication.
 *
 * <p>
 * Rules: https only; the URL must not carry user info; no redirect (a 3xx is an
 * error, so is any status other than 200); the resolved addresses must not be
 * loopback, private (10/8, 172.16/12, 192.168/16, fc00::/7), link-local
 * (169.254/16 with the cloud metadata address, fe80::/10), any-local,
 * multicast, CGNAT (100.64/10), documentation, benchmarking or reserved, nor an
 * IPv4-mapped or 6to4 IPv6 address of those; IPv6 must be global unicast
 * (2000::/3). The connection (TCP) and the request (TLS handshake, request and
 * whole response) each have their own timeout; the body is read up to a
 * maximum size, beyond which the fetch is aborted. Content-Encoding other than
 * identity is refused (no decompression bomb).
 *
 * <p>
 * Residual risk: the DNS lookup goes through the system resolver, whose timeout
 * is not bounded by the limits below.
 */
@Component
public class DefaultSafeHttpFetcher implements SafeHttpFetcher {

  /** Limits of a fetch; {@link #defaults()} = 3 s connect, 3 s request, 64 KB body, 16 KB headers. */
  public record Limits(Duration connectTimeout, Duration requestTimeout, int maxBodyBytes, int maxHeaderBytes) {
    public static Limits defaults() {
      return new Limits(Duration.ofSeconds(3), Duration.ofSeconds(3), 64 * 1024, 16 * 1024);
    }
  }

  /** Resolves a host name to its addresses (the system resolver by default). */
  @FunctionalInterface
  public interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  /**
   * Opens a TCP connection to an already checked address. The default
   * implementation returns an {@link SSLSocket} bound to {@code host} for SNI and
   * certificate verification; the handshake is started by the fetcher, under the
   * request timeout.
   */
  @FunctionalInterface
  public interface Transport {
    Socket connect(InetAddress address, int port, String host, Duration connectTimeout) throws IOException;
  }

  private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
  private static final int MAX_URL_LENGTH = 2048;
  private static final int MAX_LINE_BYTES = 8 * 1024;

  private final Limits limits;
  private final HostResolver resolver;
  private final Transport transport;

  public DefaultSafeHttpFetcher() {
    this(Limits.defaults(), InetAddress::getAllByName, new TlsTransport());
  }

  public DefaultSafeHttpFetcher(Limits limits, HostResolver resolver, Transport transport) {
    this.limits = limits;
    this.resolver = resolver;
    this.transport = transport;
  }

  @Override
  public Response fetch(String url, String accept) throws SafeFetchException {
    var target = parseUrl(url);
    if (accept != null && (accept.indexOf('\r') >= 0 || accept.indexOf('\n') >= 0)) {
      throw new SafeFetchException("Invalid Accept header");
    }
    var addresses = resolve(target.host());
    try (var socket = connect(addresses, target)) {
      var deadline = System.nanoTime() + limits.requestTimeout().toNanos();
      var in = new BufferedInputStream(new DeadlineInputStream(socket, socket.getInputStream(), deadline), 8192);
      arm(socket, deadline);
      if (socket instanceof SSLSocket ssl) {
        ssl.startHandshake();
      }
      var out = socket.getOutputStream();
      out.write(request(target, accept).getBytes(StandardCharsets.US_ASCII));
      out.flush();
      return readResponse(in);
    } catch (SafeFetchException e) {
      throw e;
    } catch (IOException e) {
      throw new SafeFetchException("Fetch of " + url + " failed: " + e.getMessage(), e);
    }
  }

  // ------------------------------------------------------------------ URL

  record Target(String host, int port, String pathAndQuery, String hostHeader) {
  }

  static Target parseUrl(String url) throws SafeFetchException {
    if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
      throw new SafeFetchException("Invalid URL");
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new SafeFetchException("Invalid URL: " + e.getMessage());
    }
    if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
      throw new SafeFetchException("Only https URLs are fetched");
    }
    if (uri.getRawUserInfo() != null) {
      throw new SafeFetchException("The URL must not contain user info");
    }
    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new SafeFetchException("The URL has no host");
    }
    host = host.toLowerCase(Locale.ROOT);
    var bracketed = host.startsWith("[") && host.endsWith("]");
    var bareHost = bracketed ? host.substring(1, host.length() - 1) : host;
    var port = uri.getPort() == -1 ? 443 : uri.getPort();
    if (port < 1 || port > 65535) {
      throw new SafeFetchException("Invalid port");
    }
    var path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    var pathAndQuery = uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
    var hostHeader = (bracketed ? host : bareHost) + (port == 443 ? "" : ":" + port);
    return new Target(bareHost, port, pathAndQuery, hostHeader);
  }

  private static String request(Target target, String accept) {
    return "GET " + target.pathAndQuery() + " HTTP/1.1\r\n"
        + "Host: " + target.hostHeader() + "\r\n"
        + "Accept: " + (accept != null ? accept : "*/*") + "\r\n"
        + "Accept-Encoding: identity\r\n"
        + "User-Agent: mtac-safe-fetcher\r\n"
        + "Connection: close\r\n"
        + "\r\n";
  }

  // ---------------------------------------------------------- resolution

  private InetAddress[] resolve(String host) throws SafeFetchException {
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(host);
    } catch (UnknownHostException e) {
      throw new SafeFetchException("Unknown host " + host, e);
    }
    if (addresses == null || addresses.length == 0) {
      throw new SafeFetchException("Unknown host " + host);
    }
    // every address must be public: a mixed answer is refused as a whole
    for (var address : addresses) {
      if (!isPublicAddress(address)) {
        throw new SafeFetchException("The host " + host + " resolves to a non public address");
      }
    }
    return addresses;
  }

  private Socket connect(InetAddress[] addresses, Target target) throws IOException {
    var deadline = System.nanoTime() + limits.connectTimeout().toNanos();
    IOException last = null;
    for (var address : addresses) {
      var remaining = Duration.ofNanos(deadline - System.nanoTime());
      if (remaining.toMillis() <= 0) {
        break;
      }
      try {
        return transport.connect(address, target.port(), target.host(), remaining);
      } catch (IOException e) {
        last = e;
      }
    }
    throw last != null ? last : new SocketTimeoutException("connect timed out");
  }

  /**
   * True when the address may be reached from the server: public IPv4, or IPv6
   * global unicast (2000::/3) outside the special-purpose ranges. IPv4-mapped
   * and 6to4 addresses are judged by the IPv4 address they carry.
   */
  public static boolean isPublicAddress(InetAddress address) {
    if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
        || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
      return false;
    }
    var bytes = address.getAddress();
    if (address instanceof Inet4Address || bytes.length == 4) {
      return isPublicIpv4(bytes, 0);
    }
    return bytes.length == 16 && isPublicIpv6(bytes);
  }

  private static boolean isPublicIpv4(byte[] b, int offset) {
    int o0 = b[offset] & 0xff;
    int o1 = b[offset + 1] & 0xff;
    int o2 = b[offset + 2] & 0xff;
    if (o0 == 0 || o0 == 10 || o0 == 127) {
      return false; // this network, private, loopback
    }
    if (o0 == 100 && (o1 & 0xc0) == 64) {
      return false; // CGNAT 100.64/10
    }
    if (o0 == 169 && o1 == 254) {
      return false; // link-local, cloud metadata
    }
    if (o0 == 172 && (o1 & 0xf0) == 16) {
      return false; // private 172.16/12
    }
    if (o0 == 192 && o1 == 168) {
      return false; // private 192.168/16
    }
    if (o0 == 192 && o1 == 0 && (o2 == 0 || o2 == 2)) {
      return false; // IETF protocol assignments, TEST-NET-1
    }
    if (o0 == 192 && o1 == 88 && o2 == 99) {
      return false; // former 6to4 relay anycast
    }
    if (o0 == 198 && (o1 & 0xfe) == 18) {
      return false; // benchmarking 198.18/15
    }
    if ((o0 == 198 && o1 == 51 && o2 == 100) || (o0 == 203 && o1 == 0 && o2 == 113)) {
      return false; // TEST-NET-2, TEST-NET-3
    }
    return o0 < 224; // multicast 224/4, reserved 240/4, broadcast
  }

  private static boolean isPublicIpv6(byte[] b) {
    var mappedPrefix = true;
    for (int i = 0; i < 10; i++) {
      if (b[i] != 0) {
        mappedPrefix = false;
        break;
      }
    }
    if (mappedPrefix && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
      return isPublicIpv4(b, 12); // IPv4-mapped ::ffff:a.b.c.d
    }
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    int b2 = b[2] & 0xff;
    // global unicast only: excludes ::, ::1, IPv4-compatible, NAT64 64:ff9b::/96,
    // discard 100::/64, ULA fc00::/7, link-local fe80::/10, site-local fec0::/10, multicast ff00::/8
    if ((b0 & 0xe0) != 0x20) {
      return false;
    }
    if (b0 == 0x20 && b1 == 0x01 && (b2 & 0xfe) == 0) {
      return false; // 2001::/23 IETF protocol assignments (Teredo, benchmarking, ORCHID)
    }
    if (b0 == 0x20 && b1 == 0x01 && b2 == 0x0d && (b[3] & 0xff) == 0xb8) {
      return false; // documentation 2001:db8::/32
    }
    if (b0 == 0x3f && b1 == 0xff && (b2 & 0xf0) == 0) {
      return false; // documentation 3fff::/20
    }
    if (b0 == 0x20 && b1 == 0x02) {
      return isPublicIpv4(b, 2); // 6to4 2002:a.b.c.d::/48
    }
    return true;
  }

  // ------------------------------------------------------------ response

  private Response readResponse(InputStream in) throws IOException, SafeFetchException {
    var headerBudget = new int[] { limits.maxHeaderBytes() };
    var statusLine = readLine(in, headerBudget);
    if (statusLine == null || !statusLine.startsWith("HTTP/1.")) {
      throw new SafeFetchException("Invalid HTTP response");
    }
    var parts = statusLine.split(" ", 3);
    int status;
    try {
      status = Integer.parseInt(parts.length > 1 ? parts[1] : "");
    } catch (NumberFormatException e) {
      throw new SafeFetchException("Invalid HTTP status line");
    }
    if (status >= 300 && status < 400) {
      throw new SafeFetchException("Redirects are not followed (HTTP " + status + ")");
    }
    if (status != 200) {
      throw new SafeFetchException("Unexpected HTTP status " + status);
    }

    var headers = new HashMap<String, String>();
    String line;
    while ((line = readLine(in, headerBudget)) != null && !line.isEmpty()) {
      var colon = line.indexOf(':');
      if (colon <= 0) {
        throw new SafeFetchException("Invalid HTTP header");
      }
      var name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
      var value = line.substring(colon + 1).trim();
      headers.merge(name, value, (a, b) -> a + ", " + b);
    }
    if (line == null) {
      throw new SafeFetchException("Truncated HTTP response");
    }

    var contentEncoding = headers.get("content-encoding");
    if (contentEncoding != null && !"identity".equalsIgnoreCase(contentEncoding)) {
      throw new SafeFetchException("Unsupported Content-Encoding " + contentEncoding);
    }
    byte[] body;
    var transferEncoding = headers.get("transfer-encoding");
    if (transferEncoding != null) {
      if (!"chunked".equalsIgnoreCase(transferEncoding)) {
        throw new SafeFetchException("Unsupported Transfer-Encoding " + transferEncoding);
      }
      body = readChunked(in);
    } else if (headers.containsKey("content-length")) {
      body = readFixed(in, contentLength(headers.get("content-length")));
    } else {
      body = readToEnd(in);
    }
    return new Response(body, headers.get("content-type"), parseMaxAge(headers.get("cache-control")));
  }

  private long contentLength(String value) throws SafeFetchException {
    long length = -1;
    for (var part : value.split(",")) {
      long parsed;
      try {
        parsed = Long.parseLong(part.trim());
      } catch (NumberFormatException e) {
        throw new SafeFetchException("Invalid Content-Length");
      }
      if (parsed < 0 || (length != -1 && parsed != length)) {
        throw new SafeFetchException("Invalid Content-Length");
      }
      length = parsed;
    }
    if (length > limits.maxBodyBytes()) {
      throw new SafeFetchException("Response larger than " + limits.maxBodyBytes() + " bytes");
    }
    return length;
  }

  private byte[] readFixed(InputStream in, long length) throws IOException, SafeFetchException {
    var body = in.readNBytes((int) length);
    if (body.length != length) {
      throw new SafeFetchException("Truncated HTTP response");
    }
    return body;
  }

  private byte[] readToEnd(InputStream in) throws IOException, SafeFetchException {
    var body = in.readNBytes(limits.maxBodyBytes() + 1);
    if (body.length > limits.maxBodyBytes()) {
      throw new SafeFetchException("Response larger than " + limits.maxBodyBytes() + " bytes");
    }
    return body;
  }

  private byte[] readChunked(InputStream in) throws IOException, SafeFetchException {
    var body = new ByteArrayOutputStream();
    while (true) {
      var sizeLine = readLine(in, new int[] { MAX_LINE_BYTES });
      if (sizeLine == null) {
        throw new SafeFetchException("Truncated chunked response");
      }
      var semicolon = sizeLine.indexOf(';');
      var sizeText = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
      long size;
      try {
        size = sizeText.length() > 8 ? Long.MAX_VALUE : Long.parseLong(sizeText, 16);
      } catch (NumberFormatException e) {
        throw new SafeFetchException("Invalid chunk size");
      }
      if (size < 0) {
        throw new SafeFetchException("Invalid chunk size");
      }
      if (size == 0) {
        var trailerBudget = new int[] { limits.maxHeaderBytes() };
        String trailer;
        while ((trailer = readLine(in, trailerBudget)) != null && !trailer.isEmpty()) {
          // trailers are ignored
        }
        return body.toByteArray();
      }
      if (size > limits.maxBodyBytes() - body.size()) {
        throw new SafeFetchException("Response larger than " + limits.maxBodyBytes() + " bytes");
      }
      var chunk = in.readNBytes((int) size);
      if (chunk.length != size) {
        throw new SafeFetchException("Truncated chunked response");
      }
      body.write(chunk);
      var end = readLine(in, new int[] { MAX_LINE_BYTES });
      if (end == null || !end.isEmpty()) {
        throw new SafeFetchException("Invalid chunk delimiter");
      }
    }
  }

  /**
   * Reads one line ended by LF (a preceding CR is removed), in ISO-8859-1.
   * Returns null at end of stream before any byte.
   */
  private static String readLine(InputStream in, int[] budget) throws IOException, SafeFetchException {
    var line = new ByteArrayOutputStream();
    int c;
    var read = false;
    while ((c = in.read()) != -1) {
      read = true;
      if (--budget[0] < 0 || line.size() >= MAX_LINE_BYTES) {
        throw new SafeFetchException("HTTP headers too large");
      }
      if (c == '\n') {
        var bytes = line.toByteArray();
        var length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
      }
      line.write(c);
    }
    if (read) {
      throw new SafeFetchException("Truncated HTTP response");
    }
    return null;
  }

  /**
   * max-age of a Cache-Control header; null when absent or when the response
   * must not be cached (no-store, no-cache).
   */
  public static Duration parseMaxAge(String cacheControl) {
    if (cacheControl == null || cacheControl.isBlank()) {
      return null;
    }
    Duration maxAge = null;
    for (var directive : cacheControl.split(",")) {
      var d = directive.trim().toLowerCase(Locale.ROOT);
      if (d.equals("no-store") || d.equals("no-cache") || d.startsWith("no-cache=")) {
        return null;
      }
      if (d.startsWith("max-age=")) {
        var value = d.substring("max-age=".length()).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1);
        }
        if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
          maxAge = value.length() > 10 ? Duration.ofSeconds(Integer.MAX_VALUE) : Duration.ofSeconds(Long.parseLong(value));
        }
      }
    }
    return maxAge;
  }

  // ---------------------------------------------------------------- I/O

  private static void arm(Socket socket, long deadline) throws IOException {
    var remainingMillis = (deadline - System.nanoTime()) / 1_000_000;
    if (remainingMillis <= 0) {
      throw new SocketTimeoutException("request timed out");
    }
    socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, remainingMillis));
  }

  /** Applies the overall request deadline to every read (and the TLS handshake). */
  private static final class DeadlineInputStream extends InputStream {
    private final Socket socket;
    private final InputStream in;
    private final long deadline;

    DeadlineInputStream(Socket socket, InputStream in, long deadline) {
      this.socket = socket;
      this.in = in;
      this.deadline = deadline;
    }

    @Override
    public int read() throws IOException {
      arm(socket, deadline);
      return in.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      arm(socket, deadline);
      return in.read(b, off, len);
    }
  }

  /** TCP connection to the checked address, wrapped in TLS for the host name. */
  static final class TlsTransport implements Transport {
    @Override
    public Socket connect(InetAddress address, int port, String host, Duration connectTimeout) throws IOException {
      var raw = new Socket();
      try {
        raw.connect(new InetSocketAddress(address, port), (int) Math.max(1, connectTimeout.toMillis()));
        var factory = SSLContext.getDefault().getSocketFactory();
        var ssl = (SSLSocket) factory.createSocket(raw, host, port, true);
        var params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // certificate checked against the host name
        if (!isIpLiteral(host)) {
          params.setServerNames(List.of(new SNIHostName(host)));
        }
        ssl.setSSLParameters(params);
        return ssl;
      } catch (NoSuchAlgorithmException e) {
        raw.close();
        throw new IOException("TLS is not available", e);
      } catch (IOException | RuntimeException e) {
        raw.close();
        throw e;
      }
    }

    private static boolean isIpLiteral(String host) {
      return host.indexOf(':') >= 0 || IPV4_LITERAL.matcher(host).matches();
    }
  }
}
