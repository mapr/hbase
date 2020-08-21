#!/usr/bin/env bash
#
#/**
# * Copyright The Apache Software Foundation
# *
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */

BASE_MAPR=${MAPR_HOME:-/opt/mapr}
MAPR_CONF="${BASE_MAPR}/conf"

# Source env.sh from MapR distribution
env=${BASE_MAPR}/conf/env.sh
[ -f $env ] && . $env

MAPR_LOGIN_CONF=${MAPR_LOGIN_CONF:-${BASE_MAPR}/conf/mapr.login.conf}
MAPR_CLUSTERS_CONF=${MAPR_CLUSTERS_CONF:-${BASE_MAPR}/conf/mapr-clusters.conf}
SSL_TRUST_STORE=${SSL_TRUST_STORE:-${BASE_MAPR}/conf/ssl_truststore}

# Set the user if not set in the environment
if [ "$HBASE_IDENT_STRING" == "" ]; then
  HBASE_IDENT_STRING=`id -nu`
fi

# Dump heap on OOM
HBASE_OPTS="$HBASE_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/cores/"

# Add MapR file system and dependency jars. There are two sets of such jars
# First set which override those found in HBase' lib folder, and is prepended
# to the CLASSPATH while the second set is appended to HBase' classpath.

# First set
# JARs in ${BASE_MAPR}/lib
MAPR_JARS="zookeeper-3.4*.jar"
for jar in ${MAPR_JARS}; do
  JARS=`echo $(ls ${BASE_MAPR}/lib/${jar} 2> /dev/null) | sed 's/\s\+/:/g'`
  if [ "${JARS}" != "" ]; then
    HBASE_MAPR_OVERRIDE_JARS=${HBASE_MAPR_OVERRIDE_JARS}:${JARS}
  fi
done
# Remove any additional ':' from the tail
HBASE_MAPR_OVERRIDE_JARS="${HBASE_MAPR_OVERRIDE_JARS#:}"

# Second set
# JARs in ${BASE_MAPR}/lib
MAPR_JARS="libprotodefs*.jar baseutils*.jar JPam-*.jar"
for jar in ${MAPR_JARS}; do
  JARS=`echo $(ls ${BASE_MAPR}/lib/${jar} 2> /dev/null) | sed 's/\s\+/:/g'`
  if [ "${JARS}" != "" ]; then
    HBASE_MAPR_EXTRA_JARS=${HBASE_MAPR_EXTRA_JARS}:${JARS}
  fi
done
# Remove any additional ':' from the tail
HBASE_MAPR_EXTRA_JARS="${HBASE_MAPR_EXTRA_JARS#:}"

export HBASE_OPTS HBASE_MAPR_OVERRIDE_JARS HBASE_MAPR_EXTRA_JARS HBASE_IDENT_STRING

# Configure secure options
if [ -z "${MAPR_SECURITY_STATUS}" -a -r "${MAPR_CLUSTERS_CONF}" ]; then
    MAPR_SECURITY_STATUS=$(head -n 1 ${MAPR_CLUSTERS_CONF} | grep secure= | sed 's/^.*secure=//' | sed 's/ .*$//')
fi

MAPR_JAAS_CONFIG_OPTS=${MAPR_JAAS_CONFIG_OPTS:-"-Djava.security.auth.login.config=${MAPR_LOGIN_CONF}"}

if [ "$MAPR_SECURITY_STATUS" = "true" ]; then
    MAPR_ZOOKEEPER_OPTS=${MAPR_ZOOKEEPER_OPTS:-"-Dzookeeper.saslprovider=com.mapr.security.maprsasl.MaprSaslProvider"}
    MAPR_ZOOKEEPER_OPTS="${MAPR_ZOOKEEPER_OPTS} -Dzookeeper.sasl.client=true"
    if (cat "$MAPR_CLUSTERS_CONF" | grep -q "kerberosEnable=true"); then
        MAPR_HBASE_SERVER_OPTS="-Dhadoop.login=kerberos_keytab"
        MAPR_HBASE_CLIENT_OPTS="-Dhadoop.login=hybrid"
    else
        MAPR_HBASE_SERVER_OPTS="-Dhadoop.login=maprsasl_keytab"
        MAPR_HBASE_CLIENT_OPTS="-Dhadoop.login=maprsasl"
    fi
    MAPR_SSL_OPTS=${MAPR_SSL_OPTS:-"-Djavax.net.ssl.trustStore=${SSL_TRUST_STORE}"}
else
    MAPR_ZOOKEEPER_OPTS=${MAPR_ZOOKEEPER_OPTS:-"-Dzookeeper.sasl.clientconfig=Client_simple -Dzookeeper.saslprovider=com.mapr.security.simplesasl.SimpleSaslProvider"}
    MAPR_HBASE_SERVER_OPTS="-Dhadoop.login=simple"
    MAPR_HBASE_CLIENT_OPTS="-Dhadoop.login=simple"
fi

export MAPR_HBASE_SERVER_OPTS="${MAPR_HBASE_SERVER_OPTS} ${MAPR_JAAS_CONFIG_OPTS} ${MAPR_ZOOKEEPER_OPTS} ${MAPR_SSL_OPTS} -Dmapr.library.flatclass"
export MAPR_HBASE_CLIENT_OPTS="${MAPR_HBASE_CLIENT_OPTS} ${MAPR_JAAS_CONFIG_OPTS} ${MAPR_ZOOKEEPER_OPTS} ${MAPR_SSL_OPTS} -Dmapr.library.flatclass"

if [ "$1" = "start" ] || [ "$1" = "foreground_start" ] || [ "$1" = "restart" ] || [ "$1" = "autorestart" ]; then
    LOG_JMX_MSGS=1
else
    LOG_JMX_MSGS=0
fi

function logJmxMsg()
{
    [ $LOG_JMX_MSGS -eq 1 ] && echo $* >> "${HBASE_JMX_LOG_FILE:-${HBASE_HOME}/logs/hbase_jmx_options.log}"
}

#Mapr JMX handling
MAPR_JMX_HBASE_MASTER_PORT=${MAPR_JMX_HBASE_MASTER_PORT:-10101}
MAPR_JMX_HBASE_REGIONSERVER_PORT=${MAPR_JMX_HBASE_REGIONSERVER_PORT:-10102}
MAPR_JMX_HBASE_THRIFTSERVER_PORT=${MAPR_JMX_HBASE_THRIFTSERVER_PORT:-10103}
MAPR_JMX_HBASE_RESTSERVER_PORT=${MAPR_JMX_HBASE_RESTSERVER_PORT:-10104}

if [ -z "$MAPR_JMXLOCALBINDING" ]; then
    MAPR_JMXLOCALBINDING="false"
fi

if [ -z "$MAPR_JMXAUTH" ]; then
    MAPR_JMXAUTH="false"
fi

if [ -z "$MAPR_JMXSSL" ]; then
    MAPR_JMXSSL="false"
fi

if [ -z "$MAPR_JMX_LOGIN_CONFIG" ]; then
	  MAPR_JMX_LOGIN_CONFIG="JMX_AGENT_LOGIN"
fi

if [ -z "$MAPR_JMXDISABLE" ] && [ -z "$MAPR_JMXLOCALHOST" ] && [ -z "$MAPR_JMXREMOTEHOST" ]; then
    logJmxMsg "No MapR JMX options given - defaulting to local binding"
fi

if [ -z "$MAPR_JMXDISABLE" ] || [ "$MAPR_JMXDISABLE" = 'false' ]; then
    # default setting for localBinding
    MAPR_JMX_OPTS="-Dcom.sun.management.jmxremote"

    if [ "$MAPR_JMXLOCALHOST" = "true" ] && [ "$MAPR_JMXREMOTEHOST" = "true" ]; then
        logJmxMsg "WARNING: Both MAPR_JMXLOCALHOST and MAPR_JMXREMOTEHOST options are enabled - defaulting to MAPR_JMXLOCALHOST config"
        MAPR_JMXREMOTEHOST=false
    fi

    if [ "$MAPR_SECURITY_STATUS" = "true" ] && [ "$MAPR_JMXREMOTEHOST" = "true" ]; then
        JMX_JAR=$(echo ${MAPR_HOME:-/opt/mapr}/lib/jmxagent*)
        if [ -n "$JMX_JAR" ] && [ -f ${JMX_JAR} ]; then
            MAPR_JMX_OPTS="-javaagent:$JMX_JAR -Dmapr.jmx.agent.login.config=$MAPR_JMX_LOGIN_CONFIG"
            MAPR_JMXAUTH="true"
        else
            logJmxMsg "jmxagent jar file missed"
            [ $LOG_JMX_MSGS -eq 1 ] && exit 1
        fi
    fi

    if [ "$MAPR_JMXAUTH" = "true" ]; then
        if [ "$MAPR_SECURITY_STATUS" = "true" ]; then
            if [ -f "$MAPR_LOGIN_CONF" ] && \
               [ -f "${MAPR_HOME:-/opt/mapr}/conf/jmxremote.access" ]; then
                MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.authenticate=true \
                  -Djava.security.auth.login.config=$MAPR_LOGIN_CONF \
                  -Dcom.sun.management.jmxremote.access.file=${MAPR_HOME:-/opt/mapr}/conf/jmxremote.access"
            else
                logJmxMsg "JMX password and/or access files missing - not starting since we are in secure mode"
                [ $LOG_JMX_MSGS -eq 1 ] && exit 1
            fi

            if [ "$MAPR_JMXREMOTEHOST" = "false" ]; then
                MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.login.config=$MAPR_JMX_LOGIN_CONFIG"
            fi
        else
            logJmxMsg "JMX Authentication configured - not starting since we are not in secure mode"
            [ $LOG_JMX_MSGS -eq 1 ] && exit 1
        fi
    else
        MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.authenticate=false"
    fi

    if [ "$MAPR_JMXLOCALHOST" = "true" ] || [ "$MAPR_JMXREMOTEHOST" = "true" ]; then
        if [ "$MAPR_JMXSSL" = "true" ] && [ "$MAPR_JMXLOCALHOST" = "true" ] ; then
            echo "WARNING: ssl is not implemented in localhost. Setting default to false"
            MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.ssl=false"
        else
            MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.ssl=false"
        fi
        if [ "$MAPR_JMXLOCALHOST" = "true" ]; then
            MAPR_JMX_OPTS="$MAPR_JMX_OPTS -Djava.rmi.server.hostname=localhost \
                -Dcom.sun.management.jmxremote.host=localhost \
                -Dcom.sun.management.jmxremote.local.only=true"
        fi
    fi

    if [ "$MAPR_JMXLOCALBINDING" = "true" ] && [ -z "$MAPR_JMX_OPTS" ]; then
        logJmxMsg "Enabling JMX local binding only"
        MAPR_JMX_OPTS="-Dcom.sun.management.jmxremote"
    fi
else
    logJmxMsg "JMX disabled by user request"
    MAPR_JMX_OPTS=""
fi

if [[ -n "$MAPR_JMX_OPTS" && ( "$MAPR_JMXLOCALHOST" = "true" || "$MAPR_JMXREMOTEHOST" = "true" ) ]]; then
    if [ -z "$MAPR_JMX_HBASE_MASTER_ENABLE" ] || [ "$MAPR_JMX_HBASE_MASTER_ENABLE" = "true" ]; then
        if [ "$MAPR_JMXREMOTEHOST" = "true" ] && [ "$MAPR_SECURITY_STATUS" = "true" ]; then
            export HBASE_MASTER_OPTS="$HBASE_MASTER_OPTS $MAPR_JMX_OPTS -Dmapr.jmx.agent.port=$MAPR_JMX_HBASE_MASTER_PORT"
            logJmxMsg "Jmx access enabled on all interfaces for hbase master on port $MAPR_JMX_HBASE_MASTER_PORT"
        else
            export HBASE_MASTER_OPTS="$HBASE_MASTER_OPTS $MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.port=$MAPR_JMX_HBASE_MASTER_PORT"
            if [ "$MAPR_JMXLOCALHOST" = "true" ]; then
                logJmxMsg "Jmx access enabled on localhost interface for hbase master on port $MAPR_JMX_HBASE_MASTER_PORT"
            else
                logJmxMsg "Jmx access enabled on all interfaces for hbase master on port $MAPR_JMX_HBASE_MASTER_PORT"
            fi
        fi
    fi
    if [ -z "$MAPR_JMX_HBASE_REGIONSERVER_ENABLE" ] || [ "$MAPR_JMX_HBASE_REGIONSERVER_ENABLE" = "true" ]; then
        if [ "$MAPR_JMXREMOTEHOST" = "true" ] && [ "$MAPR_SECURITY_STATUS" = "true" ]; then
            export HBASE_REGIONSERVER_OPTS="$HBASE_REGIONSERVER_OPTS $MAPR_JMX_OPTS -Dmapr.jmx.agent.port=$MAPR_JMX_HBASE_REGIONSERVER_PORT"
            logJmxMsg "Jmx access enabled on all interfaces for regionserver on port $MAPR_JMX_HBASE_REGIONSERVER_PORT"
        else
            export HBASE_REGIONSERVER_OPTS="$HBASE_REGIONSERVER_OPTS $MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.port=$MAPR_JMX_HBASE_REGIONSERVER_PORT"
            if [ "$MAPR_JMXLOCALHOST" = "true" ]; then
                logJmxMsg "Jmx access enabled on localhost interface for regionserver on port $MAPR_JMX_HBASE_REGIONSERVER_PORT"
            else
                logJmxMsg "Jmx access enabled on all interfaces for regionserver on port $MAPR_JMX_HBASE_REGIONSERVER_PORT"
            fi
        fi
    fi
    # only enable if explicitly set
    if [ -n "$MAPR_JMX_HBASE_THRIFTSERVER_ENABLE" ] && [ "$MAPR_JMX_HBASE_THRIFTSERVER_ENABLE" = "true" ]; then
        if [ "$MAPR_JMXREMOTEHOST" = "true" ] && [ "$MAPR_SECURITY_STATUS" = "true" ]; then
            export HBASE_THRIFT_OPTS="$HBASE_THRIFT_OPTS $MAPR_JMX_OPTS -Dmapr.jmx.agent.port=$MAPR_JMX_HBASE_THRIFTSERVER_PORT"
            logJmxMsg "Jmx access enabled on all interfaces for hbase thriftserver on port $MAPR_JMX_HBASE_THRIFTSERVER_PORT"
        else
            export HBASE_THRIFT_OPTS="$HBASE_THRIFT_OPTS $MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.port=$MAPR_JMX_HBASE_THRIFTSERVER_PORT"
            if [ "$MAPR_JMXLOCALHOST" = "true" ]; then
                logJmxMsg "Jmx access enabled on localhost interface for hbase thriftserver on port $MAPR_JMX_HBASE_THRIFTSERVER_PORT"
            else
                logJmxMsg "Jmx access enabled on all interfaces for hbase thriftserver on port $MAPR_JMX_HBASE_THRIFTSERVER_PORT"
            fi
        fi
    fi
    # only enable if explicitly set
    if [ -n "$MAPR_JMX_HBASE_RESTSERVER_ENABLE" ] && [ "$MAPR_JMX_HBASE_RESTSERVER_ENABLE" = "true" ]; then
        if [ "$MAPR_JMXREMOTEHOST" = "true" ] && [ "$MAPR_SECURITY_STATUS" = "true" ]; then
            export HBASE_REST_OPTS="$HBASE_REST_OPTS $MAPR_JMX_OPTS -Dmapr.jmx.agent.port=$MAPR_JMX_HBASE_RESTSERVER_PORT"
            logJmxMsg "Jmx access enabled on all interfaces for hbase master on port $MAPR_JMX_HBASE_RESTSERVER_PORT"
        else
            export HBASE_REST_OPTS="$HBASE_REST_OPTS $MAPR_JMX_OPTS -Dcom.sun.management.jmxremote.port=$MAPR_JMX_HBASE_RESTSERVER_PORT"
            if [ "$MAPR_JMXLOCALHOST" = "true" ]; then
                logJmxMsg "Jmx access enabled on localhost interface for hbase master on port $MAPR_JMX_HBASE_RESTSERVER_PORT"
            else
                logJmxMsg "Jmx access enabled on all interfaces for hbase master on port $MAPR_JMX_HBASE_RESTSERVER_PORT"
        fi
        fi
    fi
else
    if [ -z "$MAPR_JMX_HBASE_MASTER_ENABLE" ] || [ "$MAPR_JMX_HBASE_MASTER_ENABLE" = "true" ]; then
        export HBASE_MASTER_OPTS="$HBASE_MASTER_OPTS $MAPR_JMX_OPTS"
    fi
    if [ -z "$MAPR_JMX_HBASE_REGIONSERVER_ENABLE" ] || [ "$MAPR_JMX_HBASE_REGIONSERVER_ENABLE" = "true" ]; then
        export HBASE_REGIONSERVER_OPTS="$HBASE_REGIONSERVER_OPTS $MAPR_JMX_OPTS"
    fi
    # only enable if explicitly set
    if [ -n "$MAPR_JMX_HBASE_THRIFTSERVER_ENABLE" ] && [ "$MAPR_JMX_HBASE_THRIFTSERVER_ENABLE" = "true" ]; then
        export HBASE_THRIFT_OPTS="$HBASE_THRIFT_OPTS $MAPR_JMX_OPTS"
    fi
    # only enable if explicitly set
    if [ -n "$MAPR_JMX_HBASE_RESTSERVER_ENABLE" ] && [ "$MAPR_JMX_HBASE_RESTSERVER_ENABLE" = "true" ]; then
        export HBASE_REST_OPTS="$HBASE_REST_OPTS $MAPR_JMX_OPTS"
    fi
fi