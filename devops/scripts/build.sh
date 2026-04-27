#!/bin/bash
set -e

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
. "${SCRIPT_DIR}/_initialize_package_variables.sh"
. "${SCRIPT_DIR}/_utils.sh"

cecho() {
  echo "--> $1"
}

build_hbase() {
  cecho "HBase build start"
  cecho "GIT_COMMIT          : '$GIT_COMMIT'"
  cecho "INSTALLATION_PREFIX : '$INSTALLATION_PREFIX'"
  cecho "PKG_NAME            : '$PKG_NAME'"
  cecho "MVN_HBASE_VERSION   : '$MVN_HBASE_VERSION'"
  cecho "PKG_VERSION         : '$PKG_VERSION'"
  cecho "PKG_3DIGIT_VERSION  : '$PKG_3DIGIT_VERSION'"
  cecho "TIMESTAMP           : '$TIMESTAMP'"
  cecho "PKG_INSTALL_ROOT    : '$PKG_INSTALL_ROOT'"
  cecho "DIST_DIR            : '$DIST_DIR'"
  cecho "BUILD_ROOT          : '$BUILD_ROOT'"
  cecho "mvn_goals           : '$mvn_goals'"
  cecho "mvn_args            : '$mvn_args'"

  cecho "maven start"
  mvn ${mvn_goals} ${mvn_args}

  cecho "untar start"
  mkdir -p "${BUILD_ROOT}/build"
  package_archive="hbase-assembly/target/hbase-${MVN_HBASE_VERSION}-bin.tar.gz"

  # note #1: we need to strip one level because archive contains an extra root dir.
  # probably this was a hack for old pipelines, and we could just skip
  # that root dir during archive creation.
  # note #2: actually, could we just copy the folder that was just tarred?
  tar xvf ${package_archive} --strip-components=1 -C "${BUILD_ROOT}/build"

  # HBase specific steps
  cp -rv ext-conf/* ${BUILD_ROOT}/build/conf
  mv -v ${BUILD_ROOT}/build/conf/hbase-site-mapr.xml ${BUILD_ROOT}/build/conf/hbase-site.xml

  rm -rf ${BUILD_ROOT}/build/conf.new
  mv -v ${BUILD_ROOT}/build/conf ${BUILD_ROOT}/build/conf.new;

  find ${BUILD_ROOT}/build -name "*.sh" -exec chmod 755 {} \;
  find ${BUILD_ROOT}/build -name "*.cmd" -exec rm -fv {} \;

  mkdir -p ${BUILD_ROOT}/build/logs
  chmod -R 1777 ${BUILD_ROOT}/build/logs
}

main() {
  echo "Cleaning '${BUILD_ROOT}' dir..."
  rm -rf "${BUILD_ROOT}"

  # setting up maven
  # default hbase building command: "mvn clean install assembly:single -U -DskipTests"
  mvn_goals="clean install assembly:single"
  mvn_args="-U -DskipTests"

  if [ "${DO_DEPLOY}" = "true" ] && [ "${OS}" = "redhat" ]; then
    echo "Deploy is enabled"
    mvn_goals+=" deploy"
    mvn_args+=" -DaltDeploymentRepository=deploy-repo-override::default::${MAPR_MAVEN_REPO}"
  fi

  echo "Building project..."
  build_hbase

  echo "Preparing directory structure..."
  setup_role "mapr-hbase"
  setup_role "mapr-hbase-master"
  setup_role "mapr-hbase-regionserver"
  setup_role "mapr-hbase-rest"
  setup_role "mapr-hbase-thrift"

  setup_package "mapr-hbase"

  echo "Building packages..."
  build_package "mapr-hbase"
  build_package "mapr-hbase-master"
  build_package "mapr-hbase-regionserver"
  build_package "mapr-hbase-rest"
  build_package "mapr-hbase-thrift"

  echo "Resulting packages:"
  find "${DIST_DIR}" -exec readlink -f {} \;
}

main
