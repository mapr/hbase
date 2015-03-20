/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#ifndef TEST_NATIVE_PERFTEST_TESTOPTIONS_H_
#define TEST_NATIVE_PERFTEST_TESTOPTIONS_H_

#include <string.h>
#include <hbase/hbase.h>

#include "test_types.h"
#include "byte_buffer.h"

namespace hbase {
namespace test {

static char *kDefaultKeyPrefix = (char*) "user";

struct TestOptions {
  Workload workload_;

  bytebuffer table_;

  uint32_t numThreads_;

  /**
   * Start row for this client, not just for individual runners
   */
  uint64_t startRow_;

  /**
   * Total number of ops for this client, not just for individual runners
   */
  uint64_t numOps_;

  byte_t *keyPrefix_;

  uint32_t valueLen_;
  uint32_t putPercent_;

  uint32_t maxRowsPerScan_;
  uint32_t maxPendingRPCsPerThread_;

  uint32_t numFamilies_;
  bytebuffer *families_;

  uint32_t numColumns_;
  bytebuffer *columns_;

  uint32_t resumeThreshold_;

  uint32_t flushBatchSize_;

  bool checkRead_;
  bool hashKeys_;
  bool printKeys_;
  bool staticValues_;
  bool bufferPuts_;
  bool writeToWAL_;
  bool resendNoBufs_;

  volatile bool stopTest_;

  TestOptions() {
    workload_ = Mixed;

    table_    = NULL;
    columns_  = NULL;
    families_ = NULL;

    keyPrefix_ = (byte_t *)kDefaultKeyPrefix;

    startRow_         = 1;
    numOps_           = 1000000;

    numThreads_       = 1;
    valueLen_         = 1024;
    flushBatchSize_   = 0;
    putPercent_       = 100;
    numFamilies_      = 1;
    numColumns_       = 1;
    maxRowsPerScan_   = 50;
    resumeThreshold_  = 20000;
    maxPendingRPCsPerThread_ = 0;

    checkRead_    = false;
    hashKeys_     = true;
    bufferPuts_   = true;
    writeToWAL_   = true;
    printKeys_    = false;
    staticValues_ = false;
    resendNoBufs_ = false;

    stopTest_     = false;
  }

};

} /* namespace test */
} /* namespace hbase */

#endif /* TEST_NATIVE_PERFTEST_TESTOPTIONS_H_ */
