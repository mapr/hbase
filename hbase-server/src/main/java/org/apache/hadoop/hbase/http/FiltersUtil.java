package org.apache.hadoop.hbase.http;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;

import static org.apache.hadoop.hbase.HConstants.CUSTOM_HEADERS_FILE;

public class FiltersUtil {

  public static void addCustomHeadersFilterIfPresent(Context context, Configuration configuration) {
    String headersFileLocation = configuration.get(CUSTOM_HEADERS_FILE);
    if (!StringUtils.isEmpty(headersFileLocation)) {
      context.addFilter(makeCustomHeadersFilter(headersFileLocation), "/*", Handler.ALL);
    }
  }

  private static FilterHolder makeCustomHeadersFilter(String headersFileLocation) {
    FilterHolder customHeadersFilter = new FilterHolder(CustomHeadersFilter.class);
    customHeadersFilter.setInitParameter(CUSTOM_HEADERS_FILE, headersFileLocation);
    return customHeadersFilter;
  }
}
