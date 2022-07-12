package org.apache.hadoop.hbase.http;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

import static org.apache.hadoop.hbase.HConstants.CUSTOM_HEADERS_FILE;

public class FiltersUtil {

  public static void addCustomHeadersFilterIfPresent(ServletContextHandler contextHandler, Configuration configuration) {
    String headersFileLocation = configuration.get(CUSTOM_HEADERS_FILE);
    if (!StringUtils.isEmpty(headersFileLocation)) {
      contextHandler.addFilter(makeCustomHeadersFilter(headersFileLocation), "/*", EnumSet.allOf(DispatcherType.class));
    }
  }

  private static FilterHolder makeCustomHeadersFilter(String headersFileLocation) {
    FilterHolder customHeadersFilter = new FilterHolder(CustomHeadersFilter.class);
    customHeadersFilter.setInitParameter(CUSTOM_HEADERS_FILE, headersFileLocation);
    return customHeadersFilter;
  }
}
