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
    runner->EndRpc(err, rowSpec->key, StatKeeper::OP_SCAN);
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

  runner->EndRpc(err, rowSpec->key, StatKeeper::OP_GET);

  if (runner->checkRead_) {
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
OpsRunner::PutCallback(
    int32_t err,
    hb_client_t client,
    hb_mutation_t mutation,
    hb_result_t result,
    void* extra) {
  RowSpec *rowSpec = reinterpret_cast<RowSpec*>(extra);
  OpsRunner *runner = dynamic_cast<OpsRunner *>(rowSpec->runner);

  runner->EndRpc(err, rowSpec->key, StatKeeper::OP_PUT);

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
    StatKeeper::OpType type)  {
  semaphore_->Release();
  statKeeper_->RpcComplete(err, type);
  const char *opName = StatKeeper::GetOpName(type);
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
        (semaphore_->NumAcquired() < (numSemsAcquiredOnPause_ - resumeThreshold_))) {
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
OpsRunner::SendPut(uint64_t row) {
  hb_put_t put = NULL;
  RowSpec *rowSpec = new RowSpec(numFamilies_ * numColumns_);
  rowSpec->runner = this;
  rowSpec->key = load_
      ? generateRowKey(keyPrefix_, hashKeys_, row)
      : keyGenerator_->NextRowKey(keyPrefix_, hashKeys_);

  hb_put_create(rowSpec->key->buffer, rowSpec->key->length, &put);
  hb_mutation_set_table(put, (const char *)table_->buffer, table_->length);
  hb_mutation_set_bufferable(put, bufferPuts_);
  hb_mutation_set_durability(put, (writeToWAL_ ? DURABILITY_USE_DEFAULT : DURABILITY_SKIP_WAL));

  uint32_t cellNum = 0;
  for (uint32_t i = 0; i < numFamilies_; ++i) {
    for (uint32_t j = 0; j < numColumns_; ++j) {
      cell_data_t *cell_data = &rowSpec->cells[cellNum++];
      hb_cell_t *cell = &cell_data->hb_cell;

      cell->row = rowSpec->key->buffer;
      cell->row_len = rowSpec->key->length;

      const bytebuffer family = families_[i];
      cell->family = family->buffer;
      cell->family_len = family->length;

      const bytebuffer column = columns_[j];
      cell->qualifier = column->buffer;
      cell->qualifier_len = column->length;

      cell_data->value = bytebuffer_random(valueLen_);
      cell->value = cell_data->value->buffer;
      cell->value_len = cell_data->value->length;
      cell->ts = HBASE_LATEST_TIMESTAMP;

      hb_put_add_cell(put, cell);
    }
  }

  HBASE_LOG_TRACE("Sending put with row key : '%.*s'.",
                  rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_mutation_send(client_, put, OpsRunner::PutCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, StatKeeper::OP_PUT, true);
}

void
OpsRunner::SendScan(uint64_t row) {
  hb_scanner_t scanner = NULL;
  RowSpec *rowSpec = new RowSpec(0);
  rowSpec->runner = this;
  rowSpec->key = keyGenerator_->NextRowKey(keyPrefix_, hashKeys_);

  hb_scanner_create(client_, &scanner);
  hb_scanner_set_start_row(scanner, rowSpec->key->buffer, rowSpec->key->length);

  uint64_t totalScanRows = scanNumRowsGenerator_.NextInt64();
  hb_scanner_set_num_max_rows(scanner, totalScanRows);
  rowSpec->maxRowsToScan = totalScanRows;

  hb_scanner_set_table(scanner, (const char *)table_->buffer, table_->length);

  HBASE_LOG_TRACE("Sending row with row key : '%.*s'.",
                  rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_scanner_next(scanner, ScanCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, StatKeeper::OP_SCAN, false);
}

void
OpsRunner::SendGet(uint64_t row) {
  hb_get_t get = NULL;
  RowSpec *rowSpec = new RowSpec(0);
  rowSpec->runner = this;
  rowSpec->key = keyGenerator_->NextRowKey(keyPrefix_, hashKeys_);

  hb_get_create(rowSpec->key->buffer, rowSpec->key->length, &get);
  hb_get_set_table(get, (const char *)table_->buffer, table_->length);

  HBASE_LOG_TRACE("Sending row with row key : '%.*s'.",
      rowSpec->key->length, rowSpec->key->buffer);
  uint64_t startTime = currentTimeMicroSeconds();
  hb_get_send(client_, get, GetCallback, rowSpec);
  uint64_t endTime = currentTimeMicroSeconds();
  statKeeper_->UpdateStats(1, endTime - startTime, StatKeeper::OP_GET, false);
}

void*
OpsRunner::Run() {
  uint64_t endRow = startRow_ + numOps_;
  HBASE_LOG_INFO("Starting OpsRunner(0x%08x) for start row %llu"
      ", operation count %llu.", Id(), startRow_, numOps_);

  double rand_max = RAND_MAX;
  for (uint64_t row = startRow_; row < endRow; ++row) {
    BeginRpc(); // ensures that we have permit to send the rpc

    if (scanOnly_) {
      SendScan(row);
    } else if (load_) {
      putsSent_++;
      SendPut(row);
    } else {
      double p = rand()/rand_max;
      if (((p < putWeight_) && (putsSent_ < maxPuts_) && !paused_)
          || (getsSent_ >= maxGets_)) {
        putsSent_++;
        SendPut(row);
      } else {
        getsSent_++;
        SendGet(row);
      }
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
