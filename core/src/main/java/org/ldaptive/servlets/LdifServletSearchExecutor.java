/* See LICENSE for licensing and NOTICE for copyright. */
package org.ldaptive.servlets;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import javax.servlet.http.HttpServletResponse;
import org.ldaptive.SearchResponse;
import org.ldaptive.io.LdifWriter;

/**
 * Writes search results in LDIF format. See {@link AbstractServletSearchExecutor}.
 *
 * @author  Middleware Services
 */
public class LdifServletSearchExecutor extends AbstractServletSearchExecutor
{


  @Override
  protected void writeResponse(final SearchResponse result, final HttpServletResponse response)
    throws IOException
  {
    response.setContentType("text/plain");

    final LdifWriter writer = new LdifWriter(new BufferedWriter(new OutputStreamWriter(response.getOutputStream())));
    writer.write(result);
  }
}
