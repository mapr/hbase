package org.apache.hadoop.hbase.http;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import static org.apache.hadoop.hbase.HConstants.CUSTOM_HEADERS_FILE;

public class CustomHeadersFilter implements Filter {

  private Properties customHeadersProps = new Properties();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String headersFileLocation = filterConfig.getInitParameter(CUSTOM_HEADERS_FILE);
    File headersFile = new File(headersFileLocation);
    if (headersFile.exists()) {
      try {
        this.customHeadersProps.loadFromXML(new FileInputStream(headersFile));
      } catch (IOException e) {
        throw new ServletException(e);
      }
    } else {
      throw new ServletException(new FileNotFoundException("Headers file does not exist: "
          + headersFileLocation));
    }

  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    this.customHeadersProps.forEach((k, v) -> httpResponse.addHeader((String) k, (String) v));
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
  }
}
