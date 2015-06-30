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
#line 19 "ops_runner.cc" // ensures short filename in logs.

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>

#include <hbase/hbase.h>

#include "common_utils.h"
#include "test_types.h"
#include "ops_runner.h"

namespace hbase {
namespace test {

OpsRunner::OpsRunner(size_t runnerId,
                     const hb_client_t client,
                     const TestOptions *options,
                     StatKeeper *statKeeper,
                     KeyGenerator *keyGenerator)
: runnerId_(runnerId), client_(client), o_(options),
  statKeeper_(statKeeper), keyGenerator_(keyGenerator)
{
  getsSent_ = 0;
  putsSent_ = 0;
  paused_ = false;
  staticPutValue_ = NULL;
  numSemsAcquiredOnPause_ = 0;
  numOps_ = o_->numOps_ / o_->numThreads_;
  maxGets_ = numOps_ * (1.0 - (o_->putPercent_ / 100.0));
  maxPuts_ = numOps_ - maxGets_; // this must be called after maxGets_
  putWeight_ = o_->putPercent_ / 100.0;
  numCells_ = o_->numFamilies_ * o_->numColumns_;
  sleepUsOnENOBUFS_ = kDefaultSleepUsOnENOBUFS;
  semaphore_ = new Semaphore(o_->maxPendingRPCsPerThread_);
  if (o_->workload_ == Scan) {
    scanNumRowsGenerator_ = new UniformKeyGenerator(1, options->maxRowsPerScan_);
  }
  if (o_->staticValues_) {
    staticPutValue_ = bytebuffer_random(o_->valueLen_);
  }

  pthread_mutex_init(&bbufCacheMutex_, 0);
  bbufCache_.clear();

  pthread_cond_init(&pauseCond_, 0);
  pthread_mutex_init(&pauseMutex_, 0);

  pthread_mutex_lock(&pauseMutex_);
  random_.state[0] = (currentTimeMicroSeconds() & rand()) & 0xffff;
  random_.state[1] = (currentTimeMicroSeconds() & rand()) & 0xffff;
  random_.state[2] = (currentTimeMicroSeconds() & rand()) & 0xffff;
  pthread_mutex_unlock(&pauseMutex_);
  HBASE_LOG_INFO("Using random seed %d %d %d.", random_.state[0],
      random_.state[1], random_.state[2]);
}

void
OpsRunner::ScanCallback(
    int32_t err,
    hb_scanner_t scanner,
    hb_result_t *results,
    size_t numResults,
    void* extra) {

  if (err == ENOBUFS) {
    usleep(10000);
    hb_scanner_next(scanner, ScanCallback, extra);
    return;
  }

  RowSpec *rowSpec = reinterpret_cast<RowSpec*>(extra);
  OpsRunner *runner = dynamic_cast<OpsRunner *>(rowSpec->runner);

  for (uint32_t i = 0; i < numResults; ++i) {
    hb_result_destroy(results[i]);
  }

  rowSpec->totalRowsScanned += numResults;

  if ((numResults == 0) ||
      (rowSpec->totalRowsScanned == rowSpec->maxRowsToScan)) {
    rowSpec->Destroy();
    runner->EndRpc(err, rowSpec->key, rowSpec->op);
    hb_scanner_destroy(scanner, NULL, NULL);
  } else {
    hb_scanner_next(scanner, ScanCallback, extra);
  }
}

void
OpsRunner::GetCallback(
    int32_t err,
    hb_client_t client,
    hb_get_t get,
    hb_result_t result,
    void* extra) {
  RowSpec *rowSpec = reinterpret_cast<RowSpec*>(extra);
  OpsRunner *runner = dynamic_cast<OpsRunner *>(rowSpec->runner);

  runner->EndRpc(err, rowSpec->key, rowSpec->op);

  if (runner->o_->checkRead_) {
    size_t cellCount = 0;
    if (result) {
      hb_result_get_cell_count(result, &cellCount);
    }
    if (cellCount != 1) {
      HBASE_LOG_ERROR("Number of cells for row \'%.*s\' = %d.",
          rowSpec->key->length, rowSpec->key->buffer, cellCount);
    }
  }

  rowSpec->Destroy();
  hb_get_destroy(get);
  if (result) {
    hb_result_destroy(result);
  }
}

void
OpsRunner::MutationCallback(
    int32_t err,
    hb_client_t client,
    hb_mutation_t mutation,
    hb_result_t result,
    void* extra)
{
  RowSpec *rowSpec = reinterpret_cast<RowSpec*>(extra);
  OpsRunner *runner = dynamic_cast<OpsRunner *>(rowSpec->runner);

  if (err == ENOBUFS && runner->o_->resendNoBufs_) {
    HBASE_LOG_TRACE("Received ENOBUFS, sleeping for %lluus before retrying.",
        runner->sleepUsOnENOBUFS_);
    usleep(runner->sleepUsOnENOBUFS_);
    runner->sleepUsOnENOBUFS_ *= 2;
    hb_mutation_send(client, mutation, OpsRunner::MutationCallback, extra);
    return;
  }

  runner->sleepUsOnENOBUFS_ = kDefaultSleepUsOnENOBUFS;
  runner->EndRpc(err, rowSpec->key, rowSpec->op);

  bytebuffer bbufs[rowSpec->totalCells];
  bool shouldRelease = false;
  for (uint32_t i = 0; i < rowSpec->totalCells; ++i) {
    cell_data_t *cell_data = &rowSpec->cells[i];
    if (cell_data->value) {
      bbufs[i] = cell_data->value;
      shouldRelease = true;
      cell_data->value = NULL;
    }
  }

  if (shouldRelease) {
    runner->releaseByteBuffers(bbufs, rowSpec->totalCells);
  }

  rowSpec->Destroy();
  hb_mutation_destroy(mutation);
  if (result) {
    hb_result_destroy(result);
  }
}

void
OpsRunner::BeginRpc() {
  if (paused_) {
    pthread_mutex_lock(&pauseMutex_);
    while(paused_) {
      pthread_cond_wait(&pauseCond_, &pauseMutex_);
    }
    pthread_mutex_unlock(&pauseMutex_);
  }
  semaphore_->Acquire();
}

void
OpsRunner::EndRpc(
    int32_t err,
    bytebuffer key,
    ClientOps::OpType type)  {
  semaphore_->Release();
  statKeeper_->RpcComplete(err, type);
  const char *opName = ClientOps::GetOpName(type);
  if (err == 0) {
    HBASE_LOG_TRACE("%s completed for row \'%.*s\'.",
        opName, key->length, key->buffer);
    Resume();
  } else {
    if (err == ENOBUFS) {
      Pause();
    } else {
      HBASE_LOG_ERROR("%s failed for row \'%.*s\', result = %d.",
          opName, key->length, key->buffer, err);
    }
  }
}

void
OpsRunner::Pause()  {
  if (!paused_) {
    pthread_mutex_lock(&pauseMutex_);
    if (!paused_) {
      HBASE_LOG_TRACE("Pausing OpsRunner(0x%08x) operations.", Id());
      paused_ = true;
      numSemsAcquiredOnPause_ = semaphore_->NumAcquired();
    }
    pthread_mutex_unlock(&pauseMutex_);
  }
}

void
OpsRunner::Resume() {
  if (paused_) {
    pthread_mutex_lock(&pauseMutex_);
    if (paused_&&
        (semaphore_->NumAcquired()
            < (numSemsAcquiredOnPause_ - o_->resumeThreshold_))) {
      HBASE_LOG_TRACE("Resuming OpsRunner(0x%08x) operations.", Id());
      paused_ = false;
      pthread_cond_broadcast(&pauseCond_);
    }
    pthread_mutex_unlock(&pauseMutex_);
  }
}

void
OpsRunner::WaitForCompletion() {
  while(semaphore_->NumAcquired() > 0) {
    usleep(20000);
  }
}

void
OpsRunner::createByteBuffers(bytebuffer *bbufs, uint32_t num, size_t len) {
  uint32_t nBuf = 0;
  pthread_mutex_lock(&bbufCacheMutex_);
  while (bbufCache_.size() > 0) {
    bytebuffer bbuf = bbufCache_.back();
    bbufCache_.pop_back();
    bbufs[nBuf ++ ] = bbuf;
    if (nBuf == num) {
      break;
    }
  }

  pthread_mutex_unlock(&bbufCacheMutex_);

  uint32_t j = nBuf;
  while (nBuf < num) {
    bbufs[nBuf ++] = bytebuffer_random(len);
  }

  for (uint32_t i = 0; i < j; ++i) {
    bytebuffer_randomize(bbufs[i], len);
  }
}

void
OpsRunner::releaseByteBuffers(bytebuffer *bbufs, uint32_t num)
{
  pthread_mutex_lock(&bbufCacheMutex_);
  for (uint32_t i = 0; i < num; ++i) {
    bbufCache_.push_back(bbufs[i]);
  }

  pthread_mutex_unlock(&bbufCacheMutex_);
  return;   
}

void
OpsRunner::SendPut(bytebuffer key) {
  hb_put_t put = NULL;
  RowSpec *rowSpec = new RowSpec(numCells_);
  rowSpec->runner = this;
  rowSpec->key = key;
  rowSpec->op = ClientOps::OP_PUT;

  hb_put_create(rowSpec->key->buffer, rowSpec->key->length, &put);
  hb_mutation_set_table(put, (const char *)o_->table_->buffer, o_->table_->length);
  hb_mutation_set_bufferable(put, o_->bufferPuts_);
  hb_mutation_set_durability(put, (o_->writeToWAL_ ? DURABILITY_USE_DEFAULT : DURABILITY_SKIP_WAL));

  uint32_t cellNum = 0;
  uint32_t totalCells = o_->numFamilies_ * o_->numColumns_;
  bytebuffer bbufs[totalCells];

  if (!o_->staticValues_) {
    createByteBuffers(bbufs, totalCells, o_->valueLen_);
  }

  for (uint32_t i = 0; i < o_->numFamilies_; ++i) {
    for (uint32_t j = 0; j < o_->numColumns_; ++j) {
      cell_data_t *cell_data = &rowSpec->cells[cellNum];
      hb_cell_t *cell = &cell_data->hb_cell;

      cell->row = rowSpec->key->buffer;
      cell->row_len = rowSpec->key->length;

      const bytebuffer family = o_->families_[i];
      cell->family = family->buffer;
      cell->family_len = family->length;

      const bytebuffer column = o_->columns_[j];
      cell->qualifier = column->buffer;
      cell->qualifier_len = column->length;

      if (o_->staticValues_) {
        cell->value = staticPutValue_->buffer;
        cell->value_len = staticPutValue_->length;
      } else {
        cell_data->value = bbufs[cellNum];
        cell->value = cell_data->value->buffer;
        cell->value_len = cell_data->value->length;
      }

      cell->ts = HBASE_LATEST_TIMESTAMP;

      hb_put_add_cell(put, cell);
      cellNum ++;
    }
  }

  HBASE_LOG_TRACE("Sending put with row key : '%.*s'.",
                  rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_mutation_send(client_, put, OpsRunner::MutationCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, ClientOps::OP_PUT, true);
}

void
OpsRunner::SendScan(bytebuffer key) {
  hb_scanner_t scanner = NULL;
  RowSpec *rowSpec = new RowSpec(0);
  rowSpec->runner = this;
  rowSpec->key = key;
  rowSpec->op = ClientOps::OP_SCAN;

  hb_scanner_create(client_, &scanner);
  hb_scanner_set_start_row(scanner, rowSpec->key->buffer, rowSpec->key->length);

  uint64_t totalScanRows = scanNumRowsGenerator_->NextInt64(&random_);
  hb_scanner_set_num_max_rows(scanner, totalScanRows);
  rowSpec->maxRowsToScan = totalScanRows;

  hb_scanner_set_table(scanner,
      (const char *)o_->table_->buffer, o_->table_->length);

  HBASE_LOG_TRACE("Sending row with row key : '%.*s'.",
                  rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_scanner_next(scanner, ScanCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, ClientOps::OP_SCAN, false);
}

void
OpsRunner::SendGet(bytebuffer key) {
  hb_get_t get = NULL;
  RowSpec *rowSpec = new RowSpec(0);
  rowSpec->runner = this;
  rowSpec->key = key;
  rowSpec->op = ClientOps::OP_GET;

  hb_get_create(rowSpec->key->buffer, rowSpec->key->length, &get);
  hb_get_set_table(get, (const char *)o_->table_->buffer, o_->table_->length);

  HBASE_LOG_TRACE("Sending row with row key : '%.*s'.",
      rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_get_send(client_, get, GetCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, ClientOps::OP_GET, false);
}

#ifdef HAS_INCREMENT_SUPPORT
void
OpsRunner::SendIncrement(bytebuffer key)
{
  hb_increment_t inc = NULL;
  RowSpec *rowSpec = new RowSpec(numCells_);
  rowSpec->runner = this;
  rowSpec->key = key;
  rowSpec->op = ClientOps::OP_INCREMENT;

  hb_increment_create(rowSpec->key->buffer, rowSpec->key->length, &inc);
  hb_mutation_set_table(inc, (const char *)o_->table_->buffer, o_->table_->length);
  hb_mutation_set_durability(inc, (o_->writeToWAL_ ? DURABILITY_USE_DEFAULT : DURABILITY_SKIP_WAL));

  uint32_t cellNum = 0;
  for (uint32_t i = 0; i < o_->numFamilies_; ++i) {
    for (uint32_t j = 0; j < o_->numColumns_; ++j) {
      if (j > 0 && rand() % 2 > 0)
        continue;

      cell_data_t *cell_data = &rowSpec->cells[cellNum++];
      hb_cell_t *cell = &cell_data->hb_cell;

      cell->row = rowSpec->key->buffer;
      cell->row_len = rowSpec->key->length;

      const bytebuffer family = o_->families_[i];
      cell->family = family->buffer;
      cell->family_len = family->length;

      const bytebuffer column = o_->columns_[j];
      cell->qualifier = column->buffer;
      cell->qualifier_len = column->length;

      hb_increment_add_column(inc, cell, rand() % kMaxIncrementSize);
    }
  }

  HBASE_LOG_TRACE("Sending increment with row key : '%.*s'.",
                  rowSpec->key->length, rowSpec->key->buffer);

  uint64_t startTime = currentTimeMicroSeconds();
  hb_mutation_send(client_, inc, OpsRunner::MutationCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, ClientOps::OP_INCREMENT, true);
}
#endif

void*
OpsRunner::Run() {
  uint64_t numOps = o_->numOps_ / o_->numThreads_;
  uint64_t startRow = o_->startRow_ + (runnerId_* numOps);
  uint64_t endRow = startRow + numOps;
  HBASE_LOG_INFO("Starting OpsRunner(0x%08x) for start row %llu"
      ", operation count %llu.", Id(), startRow, numOps);

  for (uint64_t row = startRow; row < endRow && !o_->stopTest_; ++row) {
    BeginRpc(); // ensures that we have permit to send the rpc

    bytebuffer key = keyGenerator_->NextRowKey(&random_, o_->keyPrefix_, o_->hashKeys_);
    if (o_->printKeys_) {
      HBASE_LOG_INFO("OpsRunner(0x%08x): key: %.*s", Id(), key->length, key->buffer);
    }

    switch (o_->workload_) {
      case Load:
        putsSent_++;
        SendPut(key);
        break;
      case Mixed: {
        double p = RandomDouble(&random_);
        if (((p < putWeight_) && (putsSent_ < maxPuts_) && !paused_)
            || (getsSent_ >= maxGets_)) {
          putsSent_++;
          SendPut(key);
        } else {
          getsSent_++;
          SendGet(key);
        }
        break;
      }
      case Scan:
        SendScan(key);
        break;
#ifdef HAS_INCREMENT_SUPPORT
      case Increment:
        SendIncrement(key);
        break;
#endif
      default:
        assert(0);
    }
  }

  flush_client_and_wait(client_);
  HBASE_LOG_INFO("OpsRunner(0x%08x) waiting for operations to complete.", Id());
  WaitForCompletion();
  HBASE_LOG_INFO("OpsRunner(0x%08x) complete.", Id());
  return NULL;
}

} /* namespace test */
} /* namespace hbase */
