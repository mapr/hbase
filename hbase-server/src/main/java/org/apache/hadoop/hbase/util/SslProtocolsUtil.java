package org.apache.hadoop.hbase.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;

import java.util.Arrays;

import static org.apache.hadoop.hbase.HConstants.DEFAULT_SSL_ENABLED_PROTOCOLS;
import static org.apache.hadoop.hbase.HConstants.SSL_ENABLED_PROTOCOLS;

public class SslProtocolsUtil {

    private static Configuration hbaseConf = HBaseConfiguration.create();

    public static String[] getEnabledSslProtocols() {
        String protocolsString = hbaseConf.get(SSL_ENABLED_PROTOCOLS, DEFAULT_SSL_ENABLED_PROTOCOLS);
        return Arrays.stream(protocolsString.split(",")).map(String::trim).toArray(String[]::new);
    }

    public static String getEnabledSslProtocolsString() {
        return String.join(",", getEnabledSslProtocols());
    }
}
