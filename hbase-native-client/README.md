<!---
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# hbase-native-client

Native client for HBase 0.96

This is a C  library that implements a
HBase client.  It's thread safe and libEv
based.


## Design Philosphy

Synchronous and Async versions will both be built
on the same foundation. The core foundation will
be C++.  External users wanting a C library will
have to choose either async or sync.  These
libraries will be thin veneers ontop of the C++.

We should try and follow pthreads example as much
as possible:

* Consistent naming.
* Opaque pointers as types so that binary compat is easy.
* Simple setup when the defaults are good.
* Attr structs when lots of paramters could be needed.


## Naming
All public C files will start with hbase_*.{h, cc}.  This
is to keep naming conflicts to a minimum. Anything without
the hbase_ prefix is assumed to be implementation private.

All C apis and typedefs will be prefixed with hb_.

All typedefs end with _t.

## MapR libHBase 

MapR Native client for Apache HBase
This is a JNI based, thread safe C library that implements an HBase client.

## Building the native client
```
  mvn install -Phbase-native-client -DskipTests
```

This will build the tarball containing the headers, shared library and the jar
files in the `target` directory with the following structure.

```
/
+---bin/
+---conf/
+---include/
|   +--hbase/
+---lib/
|   +---native/
+---src
    +---examples/
    |   +---async/
    +---test/
        +---native/
            +---common/
```

The headers can be found under `include` folder while the shared library to link
against is under `lib/native`.

## Building and Running Unit Tests
libHBase uses [GTest](https://code.google.com/p/googletest/) as the test framework
for unit/integration tests. During the build process, it automatically downloads
and build the GTest. You will need to have `cmake` installed on the build machine
to build the GTest framwork.

Runnig the unit tests currently requires you to set `LIBHBASE_ZK_QUORUM` to a valid
HBase Zookeeper quorum. The default is `"localhost:2181"`. This can be either set
as an environment variable or in [this configuration file](src/test/resources/config.properties).
```
LIBHBASE_ZK_QUORUM="<zk_host>:<zk_port>,..." mvn integration-test
```

## Building Applications with libHBase
For examples on how to use the APIs, please take a look at [this sample source]
(src/examples/async/example_async.c).

As the library uses JNI, you will need to have both `libhbase` and `libjvm` shared
libraries in your application's library search path. The jars required for the
library can be specified through either of the environment variables `CLASSPATH`
or `HBASE_LIB_DIR`. Custom JVM options, for example `-Xmx`, etc can be specified
using the environment variable `LIBHBASE_OPTS`.

## Performance Testing
A performance test is included with the library which currently support sequential/
random gets and puts. You can run the tests using this [shell script](bin/perftest.sh).
>>>>>>> 3b5affb... HBASE-10276: Hook hbase-native-client up to Native profile. (NOT COMPILE)
