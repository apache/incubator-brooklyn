/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.auth;

import com.google.common.base.Optional;
import static com.google.common.base.Preconditions.checkNotNull;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import com.yammer.dropwizard.logging.Log;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.StringUtil;

public class BasicAuthFilter<T> implements Filter {

  private static final Log LOG = Log.forClass(BasicAuthFilter.class);

  private static final String PREFIX = "Basic";
  private static final String HEADER_NAME = "WWW-Authenticate";
  private static final String HEADER_VALUE = PREFIX + " realm=\"%s\"";

  private final Authenticator<BasicCredentials, T> authenticator;
  private final String realm;

  public BasicAuthFilter(Authenticator<BasicCredentials, T> authenticator, String realm) {
    this.authenticator = checkNotNull(authenticator, "authenticator");
    this.realm = checkNotNull(realm, "realm");
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    try {
      if (header != null) {
        final int space = header.indexOf(' ');
        if (space > 0) {
          final String method = header.substring(0, space);
          if (PREFIX.equalsIgnoreCase(method)) {
            final String decoded = B64Code.decode(header.substring(space + 1),
                StringUtil.__ISO_8859_1);
            final int i = decoded.indexOf(':');
            if (i > 0) {
              final String username = decoded.substring(0, i);
              final String password = decoded.substring(i + 1);

              final BasicCredentials credentials = new BasicCredentials(username, password);
              final Optional<T> result = authenticator.authenticate(credentials);

              if (result.isPresent()) {
                chain.doFilter(servletRequest, servletResponse);
                return;
              }
            }
          }
        }
      }
    } catch (UnsupportedEncodingException e) {
      LOG.debug(e, "Error decoding credentials");

    } catch (IllegalArgumentException e) {
      LOG.debug(e, "Error decoding credentials");

    } catch (AuthenticationException e) {
      LOG.warn(e, "Error authentication credentials");

      response.sendError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
      return;
    }

    response.setHeader(HEADER_NAME, String.format(HEADER_VALUE, realm));
    response.setContentType(MediaType.TEXT_PLAIN);
    response.sendError(Response.Status.UNAUTHORIZED.getStatusCode(),
        "Credentials are required to access this resource.");
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
    /* not used */
  }

  @Override
  public void destroy() {
    /* not used */
  }
}
