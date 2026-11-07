package net.oneki.mtac.core.util.http;

import java.time.Duration;

/**
 * Fetches a small document from a URL given by a third party (JWKS of a client,
 * Client ID Metadata Document) without exposing the internal network: HTTPS
 * only, no redirect, the resolved address must be public, short timeout and
 * bounded size.
 */
public interface SafeHttpFetcher {

  /**
   * @param url     absolute https URL
   * @param accept  value of the Accept header, for example application/json
   * @return the body and the cache lifetime announced by the server
   * @throws SafeFetchException when the URL or the response breaks a rule, or the fetch fails
   */
  Response fetch(String url, String accept) throws SafeFetchException;

  /**
   * @param body       response body, at most the configured size
   * @param contentType Content-Type header, may be null
   * @param maxAge     max-age of Cache-Control, null when absent or no-store
   */
  record Response(byte[] body, String contentType, Duration maxAge) {
  }

  class SafeFetchException extends Exception {
    public SafeFetchException(String message) {
      super(message);
    }

    public SafeFetchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
