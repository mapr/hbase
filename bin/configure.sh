#!/usr/bin/env bash

RETURN_SUCCESS=0
RETURN_ERR_MAPR_HOME=1
RETURN_ERR_ARGS=2

MAPR_HOME=${MAPR_HOME:-/opt/mapr}

. ${MAPR_HOME}/server/common-ecosystem.sh 2> /dev/null # prevent verbose output, set by 'set -x'
if [ $? -ne 0 ]; then
  echo 'Error: Seems that MAPR_HOME is not correctly set or mapr-core is not installed.'
  exit ${RETURN_ERR_MAPR_HOME}
fi
{ set +x; } 2>/dev/null

initCfgEnv

env="$MAPR_HOME"/conf/env.sh
[ -f $env ] && . $env

# Get MAPR_USER and MAPR_GROUP
DAEMON_CONF="${MAPR_HOME}/conf/daemon.conf"

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

HB_MASTER_ROLE="hbasemaster"
HB_REGIONSERVER_ROLE="hbaseregionserver"
HB_REST_ROLE="hbaserest"
HB_THRIFT_ROLE="hbasethrift"
declare -A PORTS=( ["$HB_MASTER_ROLE"]="16000" ["$HB_REGIONSERVER_ROLE"]="16020" ["$HB_REST_ROLE"]="8080" ["$HB_THRIFT_ROLE"]="9090")

# Initialize arguments
isOnlyRoles=${isOnlyRoles:-0}

# Initialize backup options files location
HBASE_SECURE_FILE="${HBASE_HOME}/conf/.isSecure"
HBASE_SITE_CHECK_SUM_FILE="${HBASE_HOME}/conf/.hbase_site_check_sum"

# Warden-specific
MAPR_CONF_DIR=${MAPR_CONF_DIR:-"$MAPR_HOME/conf"}


# Parse options

USAGE="usage: $0 [-h] [-R] [--secure|--unsecure|--customSecure] [-EC <options>]"

while [ ${#} -gt 0 ] ; do
  case "$1" in
    --secure)
      isSecure="true";
      shift 1;;
    --unsecure)
      isSecure="false";
      shift 1;;
    --customSecure)
      isSecure="custom";
      shift 1;;
    -R)
      isOnlyRoles=1;
      shift 1;;
    -EC)
      for i in $2 ; do
        case $i in
          -R) isOnlyRoles=1 ;;
          *) : ;; # unused in Hbase
        esac
      done
      shift 2;;
    -h)
      echo "${USAGE}"
      exit ${RETURN_SUCCESS}
      ;;
    *)
      # Invalid arguments passed
      echo "${USAGE}"
      exit ${RETURN_ERR_ARGS}
  esac
done


###############################################
#    METHODS FOR XML FILES                    #
###############################################
function remove_property() {
  property_name=$1
  sed -i '/<property>/{:a;N;/<\/property>/!ba; /<name>'${property_name}'<\/name>/d}' ${HBASE_SITE}
}

function add_property() {
  property_name=$1
  property_value=$2
  sed -i -e "s|</configuration>|  <property>\n    <name>${property_name}</name>\n    <value>${property_value}</value>\n  </property>\n</configuration>|" ${HBASE_SITE}
}

function set_property() {
  property_name=$1
  property_value=$2
  if ! grep -q $property_name "$HBASE_SITE" ; then
    add_property ${property_name} ${property_value}
  else
    sed -i '/'${property_name}'/{:a;N;/<\/value>/!ba; s/<value>.*<\/value>/<value>'${property_value}'<\/value>/}' ${HBASE_SITE}
  fi
}

function add_comment(){
  line_name=$1
  sed -i -e "s|</configuration>| \n <!--${line_name}-->\n</configuration>|" ${HBASE_SITE}
}

function remove_comment(){
  line_name=$1
  sed  -i  "/<!--${line_name}-->/d" ${HBASE_SITE}
}

###############################################
#    BASIC HBASE  CONFIGURATION               #
###############################################

function configure_hbase_pid_dir() {
  sed -i 's|^.*HBASE_PID_DIR=.*|export HBASE_PID_DIR='${MAPR_HOME}'/pid|g' ${HBASE_CONF}/hbase-env.sh
}

function configure_zookeeper_quorum() {
  local zk_ips=$(getZKServers)
  local zk_quorum_property="<name>hbase.zookeeper.quorum<\/name>"
  set_property ${zk_quorum_property} ${zk_ips}
  # No need to specify port separately as 'getZKServers' function returns 'hostname:port' values
  remove_property hbase.zookeeper.property.clientPort
}

function configure_hbase_default_db() {
  local default_db=""
  if [ -e "/opt/mapr/roles/$HB_MASTER_ROLE" -o -e "/opt/mapr/roles/$HB_REGIONSERVER_ROLE" ]; then
    default_db="hbase"
  else
    default_db="maprdb"
  fi
  set_property mapr.hbase.default.db "${default_db}"
}

function change_permissions() {
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



###############################################
#    HBASE AUTHORIZATION CONFIGURATION        #
###############################################

function configure_hbase_authorization_secure() {
  if ! grep -q hbase.security.authorization "$HBASE_SITE" ; then
    add_comment "Enabling Hbase authorization"
    add_property hbase.security.authorization true
    [ ! $(grep -q hbase.security.exec.permission.checks "$HBASE_SITE") ] && add_property hbase.security.exec.permission.checks true
    [ ! $(grep -q hbase.coprocessor.master.classes "$HBASE_SITE") ] && \
      add_property hbase.coprocessor.master.classes org.apache.hadoop.hbase.security.access.AccessController
    [ ! $(grep -q hbase.coprocessor.region.classes "$HBASE_SITE") ] && \
      add_property hbase.coprocessor.region.classes \
      org.apache.hadoop.hbase.security.token.TokenProvider,org.apache.hadoop.hbase.security.access.AccessController
  fi
}

function configure_hbase_authorization_insecure() {
  remove_comment "Enabling Hbase authorization"
  remove_property hbase.security.authorization
  remove_property hbase.security.exec.permission.checks
  remove_property hbase.coprocessor.master.classes
  remove_property hbase.coprocessor.region.classes
}

###############################################
#    HBASE ENCRYPTION CONFIGURATION           #
###############################################

function configure_hbase_encryption_secure() {
  if ! grep -q hbase.security.authentication "$HBASE_SITE" ; then
    add_comment "Enabling Hbase encryption"
    add_property hbase.security.authentication maprsasl
    [ ! $(grep -q hbase.rpc.protection "$HBASE_SITE") ] && add_property hbase.rpc.protection privacy
  fi
}

function configure_hbase_encryption_insecure() {
  remove_comment "Enabling Hbase encryption"
  remove_property hbase.security.authentication
  remove_property hbase.rpc.protection
}

###############################################
#    HBASE THRIFT CONFIGURATION               #
###############################################

function configure_thrift_secure() {
  if ! grep -q hbase.thrift.ssl.enabled "$HBASE_SITE" ; then
    add_comment "Enabling Hbase thrift encryption"
    add_property hbase.thrift.ssl.enabled true
  fi
  if ! grep -q hbase.thrift.support.proxyuser "$HBASE_SITE" ; then
    add_comment "Enabling Hbase thrift impersonation"
    add_property hbase.thrift.support.proxyuser true
    [ ! $(grep -q hbase.regionserver.thrift.http "$HBASE_SITE") ] && add_property hbase.regionserver.thrift.http true
  fi
}

function configure_thrift_insecure(){
  #disable encryption
  remove_comment "Enabling Hbase thrift encryption"
  remove_property hbase.thrift.ssl.enabled

  #disable impersonation
  remove_comment "Enabling Hbase thrift impersonation"
  remove_property hbase.thrift.support.proxyuser
  remove_property hbase.regionserver.thrift.http
}

###############################################
#    HBASE REST   CONFIGURATION               #
###############################################

function configure_rest_secure() {
  if ! grep -q hbase.rest.authentication.type "$HBASE_SITE" ; then
    add_comment "Enabling Hbase REST authentication"
    add_property hbase.rest.authentication.type org.apache.hadoop.security.authentication.server.MultiMechsAuthenticationHandler
  fi
  if ! grep -q hbase.rest.ssl.enabled "$HBASE_SITE" ; then
    add_comment "Enabling Hbase REST encryption"
    add_property hbase.rest.ssl.enabled true
  fi
  if ! grep -q hbase.rest.support.proxyuser "$HBASE_SITE" ; then
    add_comment "Enabling Hbase REST impersonation"
    add_property hbase.rest.support.proxyuser true
  fi
}

function configure_rest_insecure() {
  #disable encryption
  remove_comment "Enabling Hbase REST encryption"
  remove_property hbase.rest.ssl.enabled

  #disable authentication
  remove_comment "Enabling Hbase REST authentication"
  remove_property hbase.rest.authentication.type

  #disable impersonation
  remove_comment "Enabling Hbase REST impersonation"
  remove_property hbase.rest.support.proxyuser
}


###############################################
#    CHANGES TRACKING FUNCTIONS               #
###############################################

save_new_hbase_site_check_sum(){
  local hbaseSiteCheckSum=$(cksum "$HBASE_SITE" | cut -d' ' -f1)
  echo "$hbaseSiteCheckSum" > ${HBASE_SITE_CHECK_SUM_FILE}
}

read_old_hbase_site_check_sum(){
  [ -e "${HBASE_SITE_CHECK_SUM_FILE}" ] && cat "${HBASE_SITE_CHECK_SUM_FILE}"
}

is_hbase_site_changed(){
  oldHbaseSiteCksum=$(read_old_hbase_site_check_sum)
  newHbaseSiteCksum=$(cksum "$HBASE_SITE" | cut -d' ' -f1)
  [ "$oldHbaseSiteCksum" != "$newHbaseSiteCksum" ]
}

read_secure() {
  [ -e "${HBASE_SECURE_FILE}" ] && cat "${HBASE_SECURE_FILE}"
}

write_secure() {
  echo "$1" > "${HBASE_SECURE_FILE}"
}

is_hbase_not_configured_yet(){
  [ -f "$HBASE_CONF/.not_configured_yet" ]
}


###############################################
#    ROLES CONFIGURATION                      #
###############################################

is_warden_file_already_copied() {
  local role=$1
  [ -f "$MAPR_CONF_DIR/conf.d/warden.${role}.conf" ]
}

function copy_warden_file() {
  if [ -f $HBASE_CONF/warden.${1}.conf ] ; then
    logInfo "Copying warden.${1}.conf file"
    cp -p "${HBASE_CONF}/warden.${1}.conf" "${MAPR_CONF_DIR}/conf.d/" 2>/dev/null || :
  fi
}

create_restart_file(){
  local role="$1"
  mkdir -p ${MAPR_CONF_DIR}/restart
  cat > "${MAPR_CONF_DIR}/restart/$role-${HBASE_VERSION}.restart" <<EOF
#!/bin/bash
if [ -z "${MAPR_TICKETFILE_LOCATION}" ] && [ -e "${MAPR_HOME}/conf/mapruserticket" ]; then
    export MAPR_TICKETFILE_LOCATION="${MAPR_HOME}/conf/mapruserticket"
fi
maprcli node services -action restart -name ${role} -nodes $(hostname)
EOF
  chmod +x "${MAPR_CONF_DIR}/restart/$role-${HBASE_VERSION}.restart"
  chown -R $MAPR_USER:$MAPR_GROUP "${MAPR_CONF_DIR}/restart/$role-${HBASE_VERSION}.restart"
}

register_port(){
  local role="$1"
  if checkNetworkPortAvailability "${PORTS[$role]}" 2>/dev/null; then
    registerNetworkPort "$role" "${PORTS[$role]}"
    if [ $? -ne 0 ]; then
      logWarn "$role - Failed to register port: ${PORTS[$role]}"
    fi
  else
    local service=$(whoHasNetworkPort "${PORTS[$role]}")
    if [ "$service" != "$role" ]; then
      logWarn "$role - port ${PORTS[$role]} in use by $service service"
    fi
  fi
}

configure_roles(){
  local roles=(${HB_MASTER_ROLE} ${HB_REGIONSERVER_ROLE} ${HB_REST_ROLE} ${HB_THRIFT_ROLE})
  for role in "${roles[@]}"; do
    if hasRole "${role}"; then
      register_port "${role}"
      if is_hbase_not_configured_yet || ! is_warden_file_already_copied "${role}"; then
        copy_warden_file "${role}"
      fi
      if is_hbase_site_changed && ! is_hbase_not_configured_yet; then
        create_restart_file "${role}"
      fi
    fi
  done
}



# Main part

if [ "$isOnlyRoles" == 1 ] ; then

  if is_hbase_not_configured_yet; then
    configure_hbase_pid_dir
    configure_zookeeper_quorum
  fi
  configure_hbase_default_db

  if [ "$(read_secure)" != "$isSecure" ] ; then
    if [ "$isSecure" = "true" ]; then
      configure_hbase_authorization_secure
      configure_hbase_encryption_secure
      if hasRole "$HB_THRIFT_ROLE" ; then
        configure_thrift_secure
      fi
      if hasRole "$HB_REST_ROLE" ; then
        configure_rest_secure
      fi
    elif [ "$isSecure" = "false" ]; then
      configure_hbase_authorization_insecure
      configure_hbase_encryption_insecure
      if hasRole "$HB_THRIFT_ROLE" ; then
        configure_thrift_insecure
      fi
      if hasRole "$HB_REST_ROLE" ; then
        configure_rest_insecure
      fi
    fi
    write_secure "${isSecure}"
  fi

  change_permissions
  configure_roles

  rm -f "$HBASE_HOME/conf/.not_configured_yet"

  save_new_hbase_site_check_sum
fi

exit ${RETURN_SUCCESS}
