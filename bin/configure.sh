#!/usr/bin/env bash
MAPR_HOME="${MAPR_HOME:-/opt/mapr}"
HBASE_HOME="${HBASE_HOME:-__INSTALL__}"

. "${MAPR_HOME}"/server/common-ecosystem.sh 2>/dev/null # prevent verbose output, set by 'set -x'
if [ $? -ne 0 ]; then
    echo 'Error: Seems that MAPR_HOME is not correctly set or mapr-core is not installed.'
    exit 1
fi 2>/dev/null

initCfgEnv

env="$MAPR_HOME"/conf/env.sh
[ -f "$env" ] && . "$env"

# general
hbaseBase="${MAPR_HOME}/hbase"
asyncHbaseBase="${MAPR_HOME}/asynchbase"
HBASE_VERSION_FILE="$hbaseBase/hbaseversion"
HBASE_VERSION=$(cat "$HBASE_VERSION_FILE")
HBASE_HOME="$hbaseBase/hbase-$HBASE_VERSION"
HBASE_SITE=${HBASE_HOME}/conf/hbase-site.xml
HBASE_CONF_DIR=${HBASE_HOME}/conf
userdefaultdb="mapr"

# functions
# expects two args:
#   arg1 - full path to hbase dir
#   arg2 - user to set the owner to
function ConfigureRunUserForHbase() {
    HBASE_DIR=$1 # for e.g /opt/mapr/hbase/hbase-0.90.4
    if [ ! -d "$HBASE_DIR/conf" ]; then
        return
    fi

    CURR_USER=$2
    chown -R "$CURR_USER" "${HBASE_DIR}/conf"
}

function readHbaseKeyValue() {

    asynckey=$1

    returnasyncvalue=""
    hbversionfile="${hbaseBase}/hbaseversion"
    if [ ! -f "${hbversionfile}" ]; then
        logInfo "${hbversionfile} not found, skip read defaultDB from it"
        return
    fi

    hbaseversion=$(cat "${hbversionfile}")

    # hbase 1.0 above
    aboveV1=$(expr "${hbaseversion}" : "[1-9]*")
    if [ "$aboveV1" -ge 1 ]; then

        hbasesiteconf="${hbaseBase}/hbase-${hbaseversion}/conf/hbase-site.xml"
        if [ ! -f "${hbasesiteconf}" ]; then
            echo "${hbasesiteconf} not found, skip read ${asynckey} from it"
            return
        fi

        hasKey="$(grep "${asynckey}" "$hbasesiteconf")"
        if [ "$hasKey" == "" ]; then
            return
        fi

        tmpconnfile="/tmp/hbaseconn.txt"
        totalline="$(wc -l "${hbasesiteconf}" | awk '{print $1}')"
        linenum=1

        while [ "$linenum" -le "$totalline" ]; do
            grep -i "${asynckey}" -A "$linenum" "$hbasesiteconf" >"$tmpconnfile"
            hasValue=$(grep -i "value" $tmpconnfile)
            if [ "$hasValue" != "" ]; then
                returnasyncvalue=$(awk -F'[<|>]' '/name/{connname=$3}/value/{print $3}' $tmpconnfile)
                logInfo "found ${hbasesiteconf} value of ${asynckey} is ${returnasyncvalue} at loop $linenum"
                break
            fi
            ((linenum += 1))
        done
    fi
}

function writeAsynchbaseKeyValue() {
    asynckey=$1
    asyncvalue=$2

    if [ ! -d "${asyncHbaseBase}" ]; then
        #no asynchbase directory found
        return
    fi

    if [ -f "${asyncHbaseBase}/asynchbaseversion" ]; then
        ver="$(cat "${asyncHbaseBase}/asynchbaseversion")"
    else
        ver="$(ls -lrt "${asyncHbaseBase}" | sed 's/^.*asynchbase-//' | head -2 | tail -1 | awk '{print $1}')"
    fi

    asyncHbaseConfFile="${asyncHbaseBase}/asynchbase-${ver}/conf/asynchbase.conf"
    if [ ! -f "${asyncHbaseConfFile}" ]; then
        #no asynchbase configure found
        return
    fi

    asyncdefaultdb="$(grep "${asynckey}" "$asyncHbaseConfFile" | grep -v "^[[:space:]]*#" | awk -F '=' '{print $2}')"
    if [ "x$asyncdefaultdb" != "x" ]; then
        #replace
        logInfo "replace the existing value $asyncdefaultdb of $asynckey to $asyncvalue in $asyncHbaseConfFile"
        sed -i -e '/'"${asynckey}=${asyncdefaultdb}"'/{
    s/^.*$/'"$asynckey"'='"$asyncvalue"'/
    }' "${asyncHbaseConfFile}"
    else
        #insert
        logInfo "set the value of $asynckey to $asyncvalue in $asyncHbaseConfFile"
        echo "" >>"$asyncHbaseConfFile" #start a new line in case the configure file does not have \n
        echo "$asynckey=$asyncvalue" >>"$asyncHbaseConfFile"
    fi
}

function ConfigureAsyncDefaultDB() {
    if ! hasRole asynchbase; then
        logInfo "Skipping Asynchbase Role configuration... Not found"
        return
    fi

    strdefaultdb="mapr.hbase.default.db"

    #readHbaseDefaultDB
    returnasyncvalue=""
    readHbaseKeyValue "mapr.hbase.default.db"
    connvalue="${returnasyncvalue}"
    logInfo "parsed value of mapr.hbase.default.db from ${hbasesiteconf} is ${connvalue}"
    if [ "x${connvalue}" == "x" ]; then
        logWarn "-defaultdb input $userdefaultdb is neither mapr nor hbase, and did not find value of mapr.hbase.default.db from hbase-site.xml, skip the setting for asynchbase"
        return
    else
        logWarn "use the value $connvalue from hbase configure hbase-site.xml"
    fi

    writeAsynchbaseKeyValue $strdefaultdb $connvalue

    returnasyncvalue=""
    readHbaseKeyValue "hbase.zookeeper.quorum"
    if [ "${returnasyncvalue}" == "" ]; then
        return
    else
        zkQuorum="${returnasyncvalue}"
    fi

    returnasyncvalue=""
    readHbaseKeyValue "hbase.zookeeper.property.clientPort"
    if [ "${returnasyncvalue}" != "" ]; then

        zklist=""
        zks="$(echo "$zkQuorum" | tr "," "\n")"
        for zk in $zks; do
            zk="$(echo "$zk" | tr -d " ")"
            if [ "x${zk}" == "x" ]; then
                continue
            fi
            if [ "x${zklist}" == "x" ]; then
                zklist="${zk}:${returnasyncvalue}"
            else
                zklist="${zklist},${zk}:${returnasyncvalue}"
            fi
        done
        zkQuorum="${zklist}"
    fi
    writeAsynchbaseKeyValue "hbase.zookeeper.quorum" "$zkQuorum"
}

function ConfigureHBase() {
    logInfo "Configuring Hbase"

    # Bug 13243 Hbase has been compiled with new hdfs 2 jars. Will now•
    # switch between hadoop2 jars and normal jars
    for JAR in $(find "$HBASE_HOME" -iname "*jar.hadoop2"); do
        logInfo "Renaming ${JAR:-} to ${JAR%\.*}"
        mv -f "${JAR:-}" "${JAR%\.*}"
    done

    ConfigureRunUserForHbase "$HBASE_HOME" "$MAPR_USER"

    hbaseEnv="${HBASE_CONF_DIR}/hbase-env.sh"
    hbasePidDir="HBASE_PID_DIR"
    escapedInstallDir=${MAPR_HOME//\//\\/}
    sed -i -e 's/^.*'"${hbasePidDir}"'=.*/export '"${hbasePidDir}"'='"${escapedInstallDir}"'\/pid/g' "$hbaseEnv"

    #
    # If the file exists, configure hbase-site.xml
    #
    hbaseConf="${HBASE_CONF_DIR}/hbase-site.xml"
    if [ -f "${hbaseConf}" ]; then
        zooname="<name>hbase.cluster.distributed<\/name>"
        sed -i -e '/'"$zooname"'/{
      N
      s/^.*$/'"$zooname"'\n<value>true<\/value>/
      }' "${hbaseConf}"

        ZK_SERVERS="$(getZKServers)"
        zkIPs="$(echo "${ZK_SERVERS}" | tr " " ",")"
        zooaddress="<name>hbase.zookeeper.quorum<\/name>"
        sed -i -e '/'"$zooaddress"'/{
      N
      s/^.*$/'"$zooaddress"'\n<value>'"$zkIPs"'<\/value>/
      }' "${hbaseConf}"

        zooport="<name>hbase.zookeeper.property.clientPort<\/name>"
        zkPort="$(echo "${ZK_SERVERS}" | sed 's/^.*://g')"
        sed -i -e '/'"$zooport"'/{
      N
      s/^.*$/'"$zooport"'\n<value>'"$zkPort"'<\/value>/
      }' "${hbaseConf}"
    fi

    zkname="hbase.zookeeper.quorum"

    if [ "$zkPort" != "" ]; then
        zklist=""
        zks="$(echo "$zkIPs" | tr "," "\n")"
        for zk in $zks; do
            zk="$(echo "$zk" | tr -d " ")"
            if [ "x${zk}" == "x" ]; then
                continue
            fi
            if [ "x${zklist}" == "x" ]; then
                zklist="${zk}:${zkPort}"
            else
                zklist="${zklist},${zk}:${zkPort}"
            fi
        done
        zkhostport="${zklist}"
    else
        zkhostport="${zkIPs}"
    fi
    writeAsynchbaseKeyValue "$zkname" "$zkhostport"

}

function ConfigureHBIRole() {
    if ! hasRole hbinternal; then
        logInfo "Skipping Hbase Client Role configuration... Not found"
        return
    fi
    logInfo "Configuring Hbase Client Role"
    ConfigureHBase
}

function ConfigureDefaultDB() {
    hbaseConf="${HBASE_CONF_DIR}/hbase-site.xml"
    if [ ! -f "${hbaseConf}" ]; then
        logInfo "${hbaseConf} not found, skip Configure DefaultDB"
        return
    fi

    LIB_INSTALL_DIR="${MAPR_HOME}/lib"

    maprhbaseInstalled="N"
    if [ -d "${LIB_INSTALL_DIR}" ]; then
        for MH_JAR in $(find "${LIB_INSTALL_DIR}" -name "mapr-hbase-*.jar" -print 2>/dev/null); do
            logInfo "mapr-hbase client is installed $MH_JAR"
            maprhbaseInstalled="Y"
            break
        done
    fi

    if [ "$maprhbaseInstalled" == "N" ] && [ "$userdefaultdb" == "maprdb" ]; then
        echo "WARN: default database is set to mapr, but mapr client is missing from library $LIB_INSTALL_DIR."
        logWarn "WARN: default database is set to mapr, but mapr client is missing from library $LIB_INSTALL_DIR."

        # userdefaultdb can be maprdb or hbase, no matter hmaster or hregionserver role is on or off, since a client node
        # need to choose a default database as well.
    fi

    strdefaultdb="<name>mapr.hbase.default.db<\/name>"

    grep -q "$strdefaultdb" "$hbaseConf"
    found=$?
    if [ $found -eq 0 ]; then # replace
        sed -i -e '/'"$strdefaultdb"'/{
      N
      s/^.*$/    '"$strdefaultdb"'\n    <value>'"$userdefaultdb"'<\/value>/
      }' "${hbaseConf}"
        logInfo "set property mapr.hbase.default.db to value $userdefaultdb"
    else # append
        sed -i -e '/'"<\/configuration>"'/{
      s/^.*$/  <property>\n    '"$strdefaultdb"'\n    <value>'"$userdefaultdb"'<\/value>\n  <\/property>\n<\/configuration>/
      }' "${hbaseConf}"
        logInfo "add property mapr.hbase.default.db with value $userdefaultdb"
    fi

    # In case asynchbase is installed, set it as well.
    asyncdefaultdb="mapr.hbase.default.db"
    writeAsynchbaseKeyValue "$asyncdefaultdb" "$userdefaultdb"
}

function change_permissions() {
    HBASE_DIR_CONTENT=(${HBASE_HOME}/*)
    for HBASE_DIR_UNIT in ${HBASE_DIR_CONTENT[@]}; do
        if [ "$(basename "$HBASE_DIR_UNIT")" != "logs" ]; then
            chown -R "${MAPR_USER}" "${HBASE_DIR_UNIT}"
            chgrp -R "${MAPR_GROUP}" "${HBASE_DIR_UNIT}"
        else
            chown "${MAPR_USER}" "${HBASE_DIR_UNIT}"
            chgrp "${MAPR_GROUP}" "${HBASE_DIR_UNIT}"
        fi
    done
    chmod 644 "${HBASE_SITE}"
    chmod u+x "${HBASE_HOME}"/bin/*
}

function remove_property() {
    property_name=$1
    sed -i "/<property>/,/<\/property>/!b;/<property>/{h;d};H;/<\/property/!d;x;/<name>${property_name}.*<\/name>/d" "${HBASE_SITE}"
}

function add_property() {
    property_name=$1
    property_value=$2
    if ! grep -q "$property_name" "$HBASE_SITE"; then
        sed -i -e "s|</configuration>|  <property>\n    <name>${property_name}</name>\n    <value>${property_value}</value>\n  </property>\n</configuration>|" "${HBASE_SITE}"
    fi
}

function add_comment() {
    line_name=$1
    sed -i -e "s|</configuration>| \n <!--${line_name}-->\n</configuration>|" "${HBASE_SITE}"
}

function remove_comment() {
    line_name=$1
    sed -i "/<!--${line_name}-->/d" "${HBASE_SITE}"
}

function configure_thrift_impersonation() {
    if ! grep -q hbase.regionserver.thrift.http "$HBASE_SITE"; then
        add_comment "Enabling Hbase thrift impersonation"
    fi
    add_property hbase.regionserver.thrift.http true
    add_property hbase.thrift.support.proxyuser true
}

function configure_thrift_authentication() {
    if [ "$MAPR_SECURITY_STATUS" = "true" ]; then
        if ! grep -q hbase.thrift.security.authentication "$HBASE_SITE"; then
            add_comment "Enabling Hbase thrift authentication"
        fi
        add_property hbase.thrift.security.authentication maprsasl
        if [[ $MAPR_HBASE_SERVER_OPTS != *"Dhadoop.login=maprsasl_keytab" ]]; then
            echo 'export MAPR_HBASE_SERVER_OPTS="${MAPR_HBASE_SERVER_OPTS} -Dhadoop.login=maprsasl_keytab"' >>"$env"
        fi
    fi
}

function configure_thrift_encryption() {
    if ! grep -q hbase.thrift.ssl.enabled "$HBASE_SITE"; then
        add_comment "Enabling Hbase thrift encryption"
    fi
    add_property hbase.thrift.ssl.enabled true
}

function configure_rest_authentication() {
    if ! grep -q hbase.rest.authentication.type "$HBASE_SITE"; then
        add_comment "Enabling Hbase REST authentication"
    fi
    add_property hbase.rest.authentication.type org.apache.hadoop.security.authentication.server.MultiMechsAuthenticationHandler
}

function configure_rest_encryption() {
    if ! grep -q hbase.rest.ssl.enabled "$HBASE_SITE"; then
        add_comment "Enabling Hbase REST encryption"
    fi
    add_property hbase.rest.ssl.enabled true
}

function configure_rest_impersonation() {
    if ! grep -q hbase.rest.support.proxyuser "$HBASE_SITE"; then
        add_comment "Enabling Hbase REST impersonation"
    fi
    add_property hbase.rest.support.proxyuser true
}

function configure_thrift_unsecure() {
    #disable encryption
    remove_comment "Enabling Hbase thrift encryption"
    remove_property hbase.thrift.ssl.enabled
    remove_property hbase.thrift.ssl.keystore.store

    #disable authentication
    remove_comment "Enabling Hbase thrift authentication"
    remove_property hbase.thrift.security.authentication
    sed -i "/\bMAPR_HBASE_SERVER_OPTS.*maprsasl_keytab.*/d" "$env"
}

function configure_rest_unsecure() {
    remove_comment "Enabling Hbase REST encryption"

    #disable encryption
    remove_property hbase.rest.ssl.enabled
    remove_property hbase.rest.ssl.keystore.store
}

#
# Add warden files
#
function copyWardenFile() {
    if [ -f "$HBASE_HOME/conf/warden.${1}.conf" ]; then
        cp "${HBASE_HOME}/conf/warden.${1}.conf" "${MAPR_CONF_DIR}/conf.d/" 2>/dev/null || :
        sed -i s/\${VERSION}/"$(cat ${HBASE_HOME}/../hbaseversion)"/g "${MAPR_CONF_DIR}/conf.d/warden.${1}.conf"
    fi
}

function copyWardenConfFiles() {
    mkdir -p "$MAPR_HOME"/conf/conf.d
    copyWardenFile hbasethrift
    copyWardenFile hbaserest
}

function stopService() {
    if [ -e "${MAPR_CONF_DIR}/conf.d/warden.${1}.conf" ]; then
        logInfo "Stopping hbase-$1..."
        su "${MAPR_USER}" -c "$HBASE_HOME/bin/hbase-daemon.sh stop $2" 1>/dev/null &
    fi
}

function stopServicesForRestartByWarden() {
    stopService hbaserest rest
    stopService hbasethrift thrift
}

USAGE="$0:\n\t-h|--help\t- help/usage\n\t-d|--defaultdb\t\t- default DB\n\t-s|--secure\t\t- secure cluster\n\t-u|--unsecure\t\t- insecure cluster\n\t-cs|--customSecure\t- custom secure\n\t-R\t\t\t - roles only\n\t-EC\t\t\t - eco common options "
{ OPTS=$(getopt -n "$0" -a -o d:suhR --long defaultdb:,secure,unsecure,help,EC -- "$@"); } 2>/dev/null
eval set -- "$OPTS"

SECURE=false
CUSTOM=false
HELP=false
while true; do
    case "$1" in
        -d | --defaultdb)
            userdefaultdb=$2
            shift 2
            ;;

        -s | --secure)
            SECURE=true
            shift
            ;;

        -u | --unsecure)
            SECURE=false
            shift
            ;;

        -cs | --customSecure)
            if [ -f "$HBASE_HOME/conf/.not_configured_yet" ]; then
                SECURE=true
            else
                SECURE=false
                CUSTOM=true
            fi
            shift
            ;;

        -h | --help)
            HELP=true
            shift
            ;;

        -R)
            shift
            ;;

        --EC)
            # ignoring
            shift
            ;;

        --)
            shift
            break
            ;;

        *) break ;;
    esac
done

if $HELP; then
    echo -e "$USAGE"
    exit 0
fi
if $SECURE; then
    if [ -f "$HBASE_HOME/conf/warden.hbasethrift.conf" ]; then
        configure_thrift_authentication
        configure_thrift_encryption
        configure_thrift_impersonation
    fi
    if [ -f "$HBASE_HOME/conf/warden.hbaserest.conf" ]; then
        configure_rest_authentication
        configure_rest_encryption
        configure_rest_impersonation
    fi
else
    if "$CUSTOM"; then
        exit 0
    fi
    if [ -f "$HBASE_HOME/conf/warden.hbasethrift.conf" ]; then
        configure_thrift_unsecure
    fi
    if [ -f "$HBASE_HOME/conf/warden.hbaserest.conf" ]; then
        configure_rest_unsecure
    fi
fi

ConfigureHBIRole
ConfigureDefaultDB
ConfigureAsyncDefaultDB
change_permissions
copyWardenConfFiles
stopServicesForRestartByWarden

if [ -f "$HBASE_HOME/conf/.not_configured_yet" ]; then
    rm -f "$HBASE_HOME/conf/.not_configured_yet"
fi

exit 0
