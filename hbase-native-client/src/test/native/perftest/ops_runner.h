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
#line 19 "ops_runner.h" // ensures short filename in logs.

#ifndef HBASE_TESTS_OPS_RUNNER_H_
#define HBASE_TESTS_OPS_RUNNER_H_

#include <pthread.h>
#include <hbase/hbase.h>

#include "test_types.h"
#include "byte_buffer.h"
#include "key_gen.h"
#include "stats_keeper.h"
#include "testoptions.h"

namespace hbase {
namespace test {

class OpsRunner : public TaskRunner {
public:
  OpsRunner(size_t runnerId, const hb_client_t client,
      const TestOptions* options, StatKeeper* statKeeper,
      KeyGenerator* keyGenerator);

  ~OpsRunner() {
    if (o_->staticValues_) {
      bytebuffer_free(staticPutValue_);
    }
    if (o_->workload_ == Scan) {
      delete scanNumRowsGenerator_;
    }
    delete semaphore_;
  }

protected:
  const size_t runnerId_;

  const hb_client_t client_;

  const TestOptions *o_;

  uint64_t numOps_;
  uint64_t getsSent_;
  uint64_t maxGets_;
  uint64_t putsSent_;
  uint64_t maxPuts_;
  double putWeight_;
  uint32_t numCells_;

  volatile bool paused_;
  pthread_mutex_t pauseMutex_;
  pthread_cond_t pauseCond_;

  Semaphore *semaphore_;

  StatKeeper *statKeeper_;

  KeyGenerator *keyGenerator_;

  UniformKeyGenerator *scanNumRowsGenerator_;

  uint32_t numSemsAcquiredOnPause_;

  Random random_;

  bytebuffer staticPutValue_;

  uint64_t sleepUsOnENOBUFS_;

  void *Run();

  void SendPut(bytebuffer key);

  void BeginRpc();

  void EndRpc(int32_t err, bytebuffer key, ClientOps::OpType type);

  void Pause();

  void Resume();

  void WaitForCompletion();

  void SendGet(bytebuffer key);

  void SendScan(bytebuffer key);

  void SendIncrement(bytebuffer key);

  static void MutationCallback(int32_t err, hb_client_t client,
      hb_mutation_t mutation, hb_result_t result, void *extra);

  static void GetCallback(int32_t err, hb_client_t client,
      hb_get_t get, hb_result_t result, void *extra);

  static void ScanCallback(int32_t err, hb_scanner_t scanner,
      hb_result_t *results, size_t numResults, void *extra);

  static const int32_t kMaxIncrementSize = 100;
  static const int32_t kDefaultSleepUsOnENOBUFS = 500000;
};

} /* namespace test */
} /* namespace hbase */

#endif /* HBASE_TESTS_OPS_RUNNER_H_ */
