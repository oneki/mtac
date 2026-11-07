package net.oneki.mtac.model.resource.iam.identity.application;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How a service account (application) authenticates on the token endpoint.
 * A null value on an application means {@link #CLIENT_SECRET} (applications
 * created before the field existed).
 */
public enum ApplicationAuthMethod {
  @JsonProperty("client_secret")
  CLIENT_SECRET, // client_id + secret (HTTP Basic or form parameters)
  @JsonProperty("private_key_jwt")
  PRIVATE_KEY_JWT; // signed JWT assertion, RFC 7523 section 2.2, keys in jwks or jwksUri

  public static ApplicationAuthMethod of(ApplicationAuthMethod method) {
    return method != null ? method : CLIENT_SECRET;
  }

  public static ApplicationAuthMethod of(Application application) {
    return application != null ? of(application.getTokenEndpointAuthMethod()) : CLIENT_SECRET;
  }
}
