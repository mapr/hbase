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
#ifndef HBASE_TESTS_TYPES_H_
#define HBASE_TESTS_TYPES_H_

#ifndef __STDC_FORMAT_MACROS
# define __STDC_FORMAT_MACROS
#endif
#include <inttypes.h>

#include <limits.h>
#include <pthread.h>
#include <semaphore.h>
#include <stdint.h>
#include <stdlib.h>

#include <hbase/hbase.h>

#include "byte_buffer.h"

namespace hbase {
namespace test {

typedef struct cell_data_t_ {
  bytebuffer value;
  hb_cell_t  hb_cell;
} cell_data_t;

cell_data_t* new_cell_data();

class Semaphore {
public:
  Semaphore(uint32_t numPermits=SEM_VALUE_MAX);

  ~Semaphore();

  void Acquire(uint32_t num = 1);

  void Release(uint32_t num = 1);

  uint32_t NumAcquired();

  uint32_t Drain();

private:
  uint32_t numPermits_;
  sem_t sem_;
};

class TaskRunner {
public:
  TaskRunner();

  virtual ~TaskRunner() {}

  void Start();

  void Stop();

protected:
  volatile bool Running() { return !stop_; }

  uint64_t Id() { return tid_; }

  virtual void* Run() = 0;

private:
  volatile uint64_t tid_;

  volatile bool stop_;

  pthread_t thread_;

  static void* ThreadFunction(void* arg);
};

class ClientOps {
public:
  typedef enum {
    OP_PUT = 0,
    OP_GET,
    OP_FLUSH,
    OP_SCAN,
    OP_INCREMENT,
    OP_LAST
  } OpType;

  static const char* GetOpName(OpType type) {
    if (type < 0 || type >= OP_LAST) {
      return NULL;
    }
    return OP_TYPE_NAMES[type];
  }

protected:
  static const char* const OP_TYPE_NAMES[];
};

class RowSpec {
public:
  RowSpec(uint64_t numCells);

  void Destroy();

  TaskRunner *runner;

  bytebuffer key;

  uint64_t totalCells;

  struct cell_data_t_ *cells;

  uint32_t totalRowsScanned;

  uint32_t maxRowsToScan;

  ClientOps::OpType op;
};

typedef struct {
  unsigned short state[3];
} Random;

typedef enum {
  Load,
  Mixed,
  Scan,
  Increment
} Workload;

typedef enum {
  Sequential, Uniform, Zipfian
} KeyDistribution;

#ifdef __GNUC__
  #define atomic_add32(ptr, val) __sync_fetch_and_add ((ptr), (val))
  #define atomic_sub32(ptr, val) __sync_fetch_and_sub ((ptr), (val))
  #define atomic_add64(ptr, val) __sync_fetch_and_add ((ptr), (val))
  #define atomic_sub64(ptr, val) __sync_fetch_and_sub ((ptr), (val))
#else
  #error "Need to port atomic_FNxx on this platform"
#endif

} /* namespace test */
} /* namespace hbase */

#endif /* HBASE_TESTS_TYPES_H_ */
