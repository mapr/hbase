#!/usr/bin/env bash

RETURN_SUCCESS=0
RETURN_ERR_ARGS=2

MAPR_HOME=${MAPR_HOME:-/opt/mapr}


env="$MAPR_HOME"/conf/env.sh
[ -f $env ] && . $env

# Get MAPR_USER and MAPR_GROUP
DAEMON_CONF="${MAPR_HOME}/conf/daemon.conf"
MAPR_CLUSTERS_CONF="${MAPR_HOME}/conf/mapr-clusters.conf"

MAPR_USER=${MAPR_USER:-$( [ -f "$DAEMON_CONF" ] && awk -F = '$1 == "mapr.daemon.group" { print $2 }' "$DAEMON_CONF" )}
MAPR_USER=${MAPR_USER:-"mapr"}
export MAPR_USER

MAPR_GROUP=${MAPR_GROUP:-$( [ -f "$DAEMON_CONF" ] && awk -F = '$1 == "mapr.daemon.group" { print $2 }' "$DAEMON_CONF" )}
MAPR_GROUP=${MAPR_GROUP:-"$MAPR_USER"}
export MAPR_GROUP

# general
HBASE_VERSION_FILE="$MAPR_HOME"/hbase/hbaseversion
HBASE_VERSION=$(cat "$HBASE_VERSION_FILE")
HBASE_HOME="$MAPR_HOME"/hbase/hbase-"$HBASE_VERSION"
HBASE_CONF="$MAPR_HOME"/hbase/hbase-"$HBASE_VERSION"/conf
HBASE_SITE=${HBASE_CONF}/hbase-site.xml


usage="usage: $0 -zkServer <host>:<port>"
OPTS=`getopt -a -o h -l zkServer: -- "$@"`
if [ $? != 0 ]; then
  echo ${usage}
  return ${RETURN_ERR_ARGS} 2>/dev/null || exit 2
fi
eval set -- "$OPTS"

for i ; do
  case "$i" in
    --zkServer)
        zk_host="$2";
        shift 2
        ;;
    -h)
        echo ${usage}
        return ${RETURN_ERR_ARGS} 2>/dev/null || exit 2
        ;;
    --)
        shift
        ;;
  esac
done

if [ -z "$zk_host" ]; then
  echo ${usage}
  return ${RETURN_ERR_ARGS} 2>/dev/null || exit 2
fi


###############################################
#    METHODS FOR XML FILES                    #
###############################################
remove_property() {
  property_name=$1
  sed -i '/<property>/{:a;N;/<\/property>/!ba; /<name>'${property_name}'<\/name>/d}' ${HBASE_SITE}
}

add_property() {
  property_name=$1
  property_value=$2
  sed -i -e "s|</configuration>|  <property>\n    <name>${property_name}</name>\n    <value>${property_value}</value>\n  </property>\n</configuration>|" ${HBASE_SITE}
}

set_property() {
  property_name=$1
  property_value=$2
  if ! grep -q $property_name "$HBASE_SITE" ; then
    add_property ${property_name} ${property_value}
  else
    sed -i '/'${property_name}'/{:a;N;/<\/value>/!ba; s|<value>.*</value>|<value>'${property_value}'</value>|}' ${HBASE_SITE}
  fi
}

###############################################
#    BASIC CLIENT SECURITY                    #
###############################################

change_permissions() {
  chown ${MAPR_USER}:${MAPR_GROUP} ${MAPR_HOME}/hbase
  chown ${MAPR_USER}:${MAPR_GROUP} ${HBASE_HOME}
  HBASE_DIR_CONTENT=( ${HBASE_HOME}/* )
  for HBASE_DIR_UNIT in ${HBASE_DIR_CONTENT[@]}; do
    if [ $( basename $HBASE_DIR_UNIT ) != "logs" ]; then
      chown -R ${MAPR_USER}:${MAPR_GROUP} ${HBASE_DIR_UNIT}
    else
      chown ${MAPR_USER}:${MAPR_GROUP} ${HBASE_DIR_UNIT}
    fi
  done
  chmod 644 ${HBASE_SITE}
  chmod u+x ${HBASE_HOME}/bin/*
}

configure_mapr_default_db() {
  set_property mapr.hbase.default.db maprdb
}

configure_zookeeper_quorum() {
  local zk_ips=$1
  local zk_quorum_property="<name>hbase.zookeeper.quorum<\/name>"
  set_property ${zk_quorum_property} ${zk_ips}
  # No need to specify port separately as 'getZKServers' function returns 'hostname:port' values
  remove_property hbase.zookeeper.property.clientPort
}


###############################################
#    HBASE CLIENT SECURITY                    #
###############################################

configure_hbase_client_secure() {
  if ! grep -q hbase.security.authentication "$HBASE_SITE" ; then
    add_property hbase.security.authentication maprsasl
    [ ! $(grep -q hbase.rpc.protection "$HBASE_SITE") ] && add_property hbase.rpc.protection privacy
  fi
}

configure_hbase_client_insecure() {
  remove_property hbase.security.authentication
  remove_property hbase.rpc.protection
}

###############################################
#             MAIN PART                       #
###############################################

change_permissions
configure_mapr_default_db
configure_zookeeper_quorum "${zk_host}"


if (cat "$MAPR_CLUSTERS_CONF" | grep -q "secure=true"); then
  configure_hbase_client_secure
else
  configure_hbase_client_insecure
fi

exit ${RETURN_SUCCESS}
