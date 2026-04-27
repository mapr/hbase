%undefine __check_files

summary:    HPE DataFabric Ecosystem Pack: Apache HBase Master
license:    Hewlett Packard Enterprise, CopyRight
Vendor:     Hewlett Packard Enterprise
name:       mapr-hbase-master
version:    __RELEASE_VERSION__
release:    1
prefix:     /
group:      HPE
buildarch:  noarch
requires:   mapr-hbase = __RELEASE_VERSION__


%description
Apache HBase (Master) distribution included in HPE DataFabric Software Ecosystem Pack
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
        if [ -f __PREFIX__/conf/conf.d/warden.hbmaster.conf ]; then
            rm -Rf __PREFIX__/conf/conf.d/warden.hbmaster.conf
        fi
    fi
fi

%preun
#Stopping service
DAEMON_CONF="__PREFIX__/conf/daemon.conf"
MAPR_USER=${MAPR_USER:-$([ -f "$DAEMON_CONF" ] && grep "mapr.daemon.user" "$DAEMON_CONF" | cut -d '=' -f 2)}
MAPR_USER=${MAPR_USER:-"mapr"}
HBASE_HOME="__PREFIX__/hbase/hbase-__VERSION_3DIGIT__"

if sudo -u $MAPR_USER -E "${HBASE_HOME}/bin/hbase-daemon.sh" status master &>/dev/null ; then
    RESULT=$(sudo -u $MAPR_USER -E "${HBASE_HOME}/bin/hbase-daemon.sh" stop master 2>&1)
    STATUS=$?
    if [ $STATUS -ne 0 ] ; then
        echo "$RESULT"
    fi
fi


%postun
if [ "$1" = "0" ]; then
    if [ -f __PREFIX__/conf/conf.d/warden.hbmaster.conf ]; then
        rm -f __PREFIX__/conf/conf.d/warden.hbmaster.conf
    fi
fi
