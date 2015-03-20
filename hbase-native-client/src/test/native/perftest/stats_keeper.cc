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
#line 19 "stats_keeper.cc" // ensures short filename in logs.
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <unistd.h>
#include <time.h>

#include "stats_keeper.h"

#include "test_types.h"

namespace hbase {
namespace test {

StatKeeper::OperationType::
OperationType(const char* name)
: name_(name){
  numOps_ = success_ = failure_ = 0;
  cumLatencyOps_ = 0;
  minLatencyOps_ = -1;
  maxLatencyOps_ = 0;
  opsStartTime_ = opsEndTime_ = 0;
  pthread_mutex_init(&mutexes_, NULL);
}

StatKeeper::OperationType::
~OperationType() {
  pthread_mutex_destroy(&mutexes_);
}

StatKeeper::StatKeeper() {
  bufferableOpsCount_ = 0;
  op_ = new OperationType*[OP_LAST];
  for (int i = 0; i < OP_LAST; ++i) {
    op_[i] = new OperationType(OP_TYPE_NAMES[i]);
  }
}

StatKeeper::~StatKeeper() {
  if (op_ != NULL) {
    for (int i = 0; i < OP_LAST; ++i) {
      delete op_[i];
    }
  }

  delete[] op_;
}

void
StatKeeper::RpcComplete(int32_t err, OpType type) {
  if (type >= OP_LAST) {
    return;
  }
  op_[type]->opsEndTime_ = currentTimeMicroSeconds();
  if (err) {
    atomic_add64(&op_[type]->failure_, 1);
  } else {
    atomic_add64(&op_[type]->success_, 1);
  }
}

void
StatKeeper::UpdateStats(uint32_t numOps,
    uint32_t elapsed, OpType type, bool isBufferable) {
  if (type >= OP_LAST) {
    return;
  }

  OperationType *opType = op_[type];
  pthread_mutex_lock(&opType->mutexes_);
  {
    int64_t minLatencyOps = opType->minLatencyOps_;
    int64_t maxLatencyOps = opType->maxLatencyOps_;

    if (opType->opsStartTime_ == 0) {
      // first operation
      minLatencyOps = elapsed;
      opType->opsStartTime_ = currentTimeMicroSeconds();
    }

    if (elapsed < minLatencyOps) {
      minLatencyOps = elapsed;
    }
    if (maxLatencyOps < elapsed) {
      maxLatencyOps = elapsed;
    }

    opType->minLatencyOps_ = minLatencyOps;
    opType->maxLatencyOps_ = maxLatencyOps;
    opType->numOps_ += numOps;
    opType->cumLatencyOps_ += elapsed;
    if (isBufferable) {
      bufferableOpsCount_ += numOps;
    }
  }
  pthread_mutex_unlock(&opType->mutexes_);
}

void*
StatKeeper::Run() {
  uint64_t prevNumOps[OP_LAST] = {0};
  uint64_t currentNumOps[OP_LAST] = {0};
  uint64_t prevSuccess[OP_LAST] = {0};
  uint64_t currentSuccess[OP_LAST] = {0};
  uint64_t totalOps;
  uint64_t totalOpsLastSec;
  uint64_t totalFailures;
  uint64_t totalSuccess;
  uint64_t totalSuccessLastSec;
  int32_t statsCount = 0, lastStatsCount = 0;

  int32_t nsec = 0;
  while (Running()) {
    sleep(1);
    ++nsec;
    time_t t;
    time(&t);
    struct tm *timeinfo = localtime(&t);
    int hour = timeinfo->tm_hour;
    int min = timeinfo->tm_min;
    int secs = timeinfo->tm_sec;

    statsCount = totalOps = totalOpsLastSec = 0;
    totalSuccess = totalFailures = totalSuccessLastSec = 0;
    for (int i = 0; i < OP_LAST; ++i) {
      uint64_t numOpsForOp = op_[i]->numOps_;
      uint64_t numSuccessForOp = op_[i]->success_;
      if (!numOpsForOp) continue;
      statsCount++;
      // submitted
      totalOps            += numOpsForOp;
      currentNumOps[i]    = numOpsForOp - prevNumOps[i];
      totalOpsLastSec     += currentNumOps[i];
      // successes
      totalSuccess        += numSuccessForOp;
      currentSuccess[i]   = numSuccessForOp - prevSuccess[i];
      totalSuccessLastSec += currentSuccess[i];
      // failures
      totalFailures       += op_[i]->failure_;
    }

    if ((nsec % 10) == 1 || statsCount > lastStatsCount) {
      lastStatsCount = statsCount;
      fprintf(stdout, "%8s %5s %9s %6s %8s %8s %6s",
          "Time", "Secs", "TotalOps", "Ops/s", "TotFail", "TotSuc", "Suc/s");
      for (int i = 0; i < OP_LAST; ++i) {
        uint64_t numOpsForOp = op_[i]->numOps_;
        if (!numOpsForOp) continue;
        fprintf(stdout, "|[%-6s#] %6s %8s %8s %8s",
                OP_TYPE_NAMES[i], "Ops/s",
                "avg(us)", "max(us)", "min(us)");
      }
      fprintf(stdout, "|\n");
      fflush(stdout);
    }

    fprintf(stdout, "%02d:%02d:%02d %5d %9" PRIu64 " %6" PRIu64
            " %8" PRIu64 " %8" PRIu64 " %6" PRIu64,
            hour, min, secs, nsec, totalOps, totalOpsLastSec,
            totalFailures, totalSuccess, totalSuccessLastSec);
    for (int i = 0; i < OP_LAST; ++i) {
      uint64_t numOpsForOp = op_[i]->numOps_;
      if (!numOpsForOp) continue;
      fprintf(stdout, "|%9" PRIu64 " %6" PRIu64 " %8" PRIu64 " %8" PRIu64 " %8" PRIu64 "",
              numOpsForOp, currentNumOps[i],
              (op_[i]->cumLatencyOps_ / (numOpsForOp ? numOpsForOp : 1)),
              op_[i]->maxLatencyOps_, op_[i]->minLatencyOps_);
    }
    fprintf(stdout, "|\n");
    fflush(stdout);
    for (int i = 0; i < OP_LAST; ++i) {
      prevNumOps[i]  = op_[i]->numOps_;
      prevSuccess[i] = op_[i]->success_;
    }
  }

  return NULL;
}

void
StatKeeper::PrintSummary() {
  fprintf(stdout, "============================================================================\n");
  for (int i = 0; i < OP_LAST; ++i) {
    uint64_t numOpsForOp = op_[i]->numOps_;
    if (!numOpsForOp) continue;
    uint64_t runTimeForOp = (op_[i]->opsEndTime_ - op_[i]->opsStartTime_)/1000000;
    fprintf(stdout, "[%5s] %10" PRIu64 " Ops, %" PRIu64 " Secs, %" PRIu64 " ops/s. "
            "Success: %" PRIu64 ", Failures: %" PRIu64 ". "
            "Latency(us): avg %" PRIu64 ", max %" PRIu64 ", min %" PRIu64 ".\n",
            op_[i]->name_, numOpsForOp, runTimeForOp, 
            (numOpsForOp / (runTimeForOp ? runTimeForOp : 1)),
            op_[i]->success_, op_[i]->failure_,
            (op_[i]->cumLatencyOps_ / (numOpsForOp ? numOpsForOp : 1)),
            op_[i]->maxLatencyOps_, op_[i]->minLatencyOps_);
  }
  fprintf(stdout, "============================================================================\n");
  fflush(stdout);
}

} /* namespace test */
} /* namespace hbase */
