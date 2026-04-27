%undefine __check_files

summary:     HPE DataFabric Ecosystem Pack: Apache HBase Thrift
license:     Hewlett Packard Enterprise, CopyRight
Vendor:      Hewlett Packard Enterprise
name:        mapr-hbasethrift
version:     __RELEASE_VERSION__
release:     1
prefix:      /
group:       HPE
buildarch:   noarch
requires:    mapr-hbase = __RELEASE_VERSION__
AutoReqProv: no


%description
Apache HBase (Thrift) distribution included in HPE DataFabric Software Ecosystem Pack
Tag: __RELEASE_BRANCH__
Commit: __GIT_COMMIT__


%clean
echo "NOOP"


%files
__PREFIX__/roles


%pre
if [ "$1" = "2" ]; then
    OLD_TIMESTAMP=$(rpm -qi mapr-hbase | awk -F': ' '/Version/ {print $2}')
    OLD_VERSION=$( echo $OLD_TIMESTAMP | cut -d'.' -f1-3 )

    if [ "__VERSION_3DIGIT__" != "OLD_VERSION" ] ; then
        if [ -f __PREFIX__/conf/conf.d/warden.hbasethrift.conf ]; then
            rm -Rf __PREFIX__/conf/conf.d/warden.hbasethrift.conf
        fi
    fi
fi

%post
if [ "$1" = "1" ]; then
    # To configure separate service '.isSecure' file need to be removed. If this file is present and security configuration
    # was not changed, HBase configure.sh script would not configure this separate service.
    if [ -f __PREFIX__/hbase/hbase-__VERSION_3DIGIT__/conf/.isSecure ]; then
        rm -f __PREFIX__/hbase/hbase-__VERSION_3DIGIT__/conf/.isSecure
    fi
fi

%preun
function remove_property() {
    property_name=$1
    sed -i '/<property>/{:a;N;/<\/property>/!ba; /<name>'${property_name}'<\/name>/d}' ${HBASE_SITE}
}

function remove_comment(){
    line_name=$1
    sed  -i  "/<!--${line_name}-->/d" ${HBASE_SITE}
}

# Remove secure thrift properties from hbase-site.xml

if [ "$1" = "0" ]; then
    HBASE_SITE=__PREFIX__/hbase/hbase-__VERSION_3DIGIT__/conf/hbase-site.xml
    remove_comment "Enabling Hbase thrift encryption"
    remove_property hbase.thrift.ssl.enabled
    remove_comment "Enabling Hbase thrift impersonation"
    remove_property hbase.thrift.support.proxyuser
    remove_property hbase.regionserver.thrift.http
fi

#Stopping service
DAEMON_CONF="__PREFIX__/conf/daemon.conf"
MAPR_USER=${MAPR_USER:-$([ -f "$DAEMON_CONF" ] && grep "mapr.daemon.user" "$DAEMON_CONF" | cut -d '=' -f 2)}
MAPR_USER=${MAPR_USER:-"mapr"}
HBASE_HOME="__PREFIX__/hbase/hbase-__VERSION_3DIGIT__"

if sudo -u $MAPR_USER -E "${HBASE_HOME}/bin/hbase-daemon.sh" status thrift &>/dev/null ; then
    RESULT=$(sudo -u $MAPR_USER -E "${HBASE_HOME}/bin/hbase-daemon.sh" stop thrift 2>&1)
    STATUS=$?
    if [ $STATUS -ne 0 ] ; then
        echo "$RESULT"
    fi
fi

%postun
if [ "$1" = "0" ]; then
    if [ -f __PREFIX__/conf/conf.d/warden.hbasethrift.conf ]; then
        rm -f __PREFIX__/conf/conf.d/warden.hbasethrift.conf
    fi
fi
