%undefine __check_files 

summary:     HPE DataFabric Ecosystem Pack: Apache HBase
license:     Hewlett Packard Enterprise, CopyRight
Vendor:      Hewlett Packard Enterprise
name:        mapr-hbase
version:     __RELEASE_VERSION__
release:     1
prefix:      /
group:       HPE
BuildArch:   noarch
Obsoletes:   mapr-hbase-internal
requires:    mapr-client >= 7.1.0, mapr-hadoop-client >= 3.3.3
conflicts:   mapr-core < 7.1.0
AutoReqProv: no

%description
Apache HBase distribution included in HPE DataFabric Software Ecosystem Pack
Tag: __RELEASE_BRANCH__
Commit: __GIT_COMMIT__


%clean
echo "NOOP"


%files
__PREFIX__/hbase
__PREFIX__/roles


%pre
# Saving of old configurations
if [ "$1" = "2" ]; then
    OLD_TIMESTAMP=$(rpm -qi mapr-hbase | awk -F': ' '/Version/ {print $2}')
    OLD_VERSION=$( echo $OLD_TIMESTAMP | cut -d'.' -f1-3 )
    mkdir -p __PREFIX__/hbase/hbase-${OLD_TIMESTAMP}/conf
    cp -r __PREFIX__/hbase/hbase-${OLD_VERSION}/conf/* __PREFIX__/hbase/hbase-${OLD_TIMESTAMP}/conf

    DAEMON_CONF=__PREFIX__/conf/daemon.conf
    if [ -f "$DAEMON_CONF" ]; then
        MAPR_USER=$( awk -F = '$1 == "mapr.daemon.user" { print $2 }' $DAEMON_CONF)
        MAPR_GROUP=$( awk -F = '$1 == "mapr.daemon.group" { print $2 }' $DAEMON_CONF)
        if [ ! -z "$MAPR_USER" ]; then
            chown -R ${MAPR_USER}:${MAPR_GROUP} __PREFIX__/hbase/hbase-${OLD_TIMESTAMP}
        fi
    fi

    OLD_VERSION_FILE="%{_localstatedir}/lib/rpm-state/mapr-hbase-old-version"
    echo "$OLD_VERSION" > "$OLD_VERSION_FILE"

    # If it is major update, hbase has to be configured as on fresh install
    if [ "__VERSION_3DIGIT__" != "${OLD_VERSION}" ]; then
        mkdir -p __INSTALL_3DIGIT__/conf
        touch "__INSTALL_3DIGIT__/conf/.not_configured_yet"
    fi
fi

%post
rm -f  /usr/bin/hbase
rm -f  __PREFIX__/bin/hbase
ln -sf __INSTALL_3DIGIT__/bin/hbase __PREFIX__/bin/hbase
ln -sf __INSTALL_3DIGIT__/bin/hbase /usr/bin/hbase

if [ -f "__PREFIX__/bin/versions.sh" ]; then
    sed -i 's|HBASE_VERSION=.*|HBASE_VERSION=__VERSION_3DIGIT__|g' "__PREFIX__/bin/versions.sh"
fi

mkdir -p "__INSTALL_3DIGIT__"/conf

if [ "$1" = "1" ]; then
  touch "__INSTALL_3DIGIT__/conf/.not_configured_yet"
fi

diffR=$(diff -r "__INSTALL_3DIGIT__"/conf "__INSTALL_3DIGIT__"/conf.new | grep "^Only in " | grep "conf.new" | sed "s/^Only in //" | sed "s/: /\//")
for i in ${diffR}; do
  j=$(echo "$i" | sed 's/conf.new/conf/g')
  cp -Rp "$i" "$j"
done


%postun
# Remove hbase directory if it is uninstall or if version changed and we do not need old directory
VERSION_FILE_CONTENT=$(cat __PREFIX__/hbase/hbaseversion 2>/dev/null)
if [ -n "$VERSION_FILE_CONTENT" -a "__VERSION_3DIGIT__" != "$VERSION_FILE_CONTENT" ]; then
  rm -Rf __INSTALL_3DIGIT__
fi

if [ "$1" = "0" ]; then
  rm -f __PREFIX__/bin/hbase
  rm -f /usr/bin/hbase
  rm -Rf __PREFIX__/hbase
fi

%posttrans
OLD_VERSION_FILE="%{_localstatedir}/lib/rpm-state/mapr-hbase-old-version"

# This files will exist only on upgrade
if [ -e "$OLD_VERSION_FILE" ]; then
    OLD_VERSION=$(cat "$OLD_VERSION_FILE")

    rm "$OLD_VERSION_FILE"

    # Remove directory with old version
    NEW_VERSION=$(cat __PREFIX__/hbase/hbaseversion)

    if [ "$OLD_VERSION" != "$NEW_VERSION" ]; then
        rm -rf "__PREFIX__/hbase/hbase-${OLD_VERSION}"
    fi
fi
