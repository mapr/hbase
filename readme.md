Apache HBase with MapR
======================
Introduction
------------
Apache HBase [1] is an open-source, distributed, versioned, column-oriented
store modeled after Google' Bigtable: A Distributed Storage System for
Structured Data by Chang et al.[2]  Just as Bigtable leverages the distributed
data storage provided by the Google File System, HBase provides Bigtable-like
capabilities on top of Apache Hadoop [3].

To get started using HBase, the full documentation for this release can be
found under the doc/ directory that accompanies this README.  Using a browser,
open the docs/index.html to view the project home page (or browse to [1]).
The HBase 'book' at docs/book.html has a 'quick start' section and is where you
should being your exploration of the HBase project.

The latest HBase can be downloaded from an Apache Mirror [4].

The source code can be found at [5]

The HBase issue tracker is at [6]

Apache HBase is made available under the Apache License, version 2.0 [7]

The HBase mailing lists and archives are listed here [8].

See [9] for information about using HBase with MapR.

1. http://hbase.apache.org
2. http://labs.google.com/papers/bigtable.html
3. http://hadoop.apache.org
4. http://www.apache.org/dyn/closer.cgi/hbase/
5. http://hbase.apache.org/docs/current/source-repository.html
6. http://hbase.apache.org/docs/current/issue-tracking.html
7. http://hbase.apache.org/docs/current/license.html
8. http://hbase.apache.org/docs/current/mail-lists.html
9. http://www.mapr.com/doc/display/MapR/HBase

Compiling HBase with MapR patches
---------------------------------
Clone and checkout the "&lt;hbase-version&gt;-mapr" tag or branch of the Apache HBase 
release version from the github (https://github.com/mapr/hbase). For example,
if you want to compile HBase version 0.98.12, checkout the "0.98.12-mapr" branch.

```bash
$ mkdir hbase-0.98.12-mapr
$ cd hbase-0.98.12-mapr
$ git clone git://github.com/mapr/hbase.git .
$ git checkout 0.98.12-mapr
$ mvn clean install assembly:single -DskipTests -Dgenerate.mapr.patches
```

The command line arguments `-DskipTests` and `-Dgenerate.mapr.patches` are optional
and allow you to skip running the unit test and generate MapR patches applied over
the apache release in git serial format, respectively.

Using HBase artifacts with MapR patches in your Maven Project
-------------------------------------------------------------
Add the following dependency to your project's pom.xml

```xml
<dependency>
  <groupId>org.apache.hbase</groupId>
  <artifactId>hbase-client</artifactId>
  <version>${mapr.hbase.version}</version>
</dependency>
```
