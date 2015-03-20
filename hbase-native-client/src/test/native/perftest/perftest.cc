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
#line 19 "perftest.cc" // ensures short filename in logs.

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include <hbase/hbase.h>

#include "admin_ops.h"
#include "byte_buffer.h"
#include "common_utils.h"
#include "flusher.h"
#include "key_gen.h"
#include "ops_runner.h"
#include "stats_keeper.h"
#include "test_types.h"
#include "testoptions.h"
#include "version.h"

namespace hbase {
namespace test {

static TestOptions options;

static char *argZkQuorum    = NULL;
static char *argZkRootNode  = NULL;
static char *argTableName   = (char*) "test_table";
static char *argFamilyName  = (char*) "f";
static char *argColumnName  = (char*) "c";
static char *argLogFilePath = NULL;

static uint64_t argRecordCount    = 1000000;

static uint32_t argMaxPendingRPCs = 100000;

static bool argLogDisabled = false;
static bool argCreateTable = false;

static KeyDistribution argKeyDistribution = Uniform;

static
void usage() {
  fprintf(stderr, "Usage: perftest [options]...\n"
      "Available options: (default values in [])\n"
      "  -zkQuorum <zk_quorum> [library default]\n"
      "  -zkRootNode <zk_root_node> [/hbase]\n"
      "  -table <table> [test_table]\n"
      "  -workload load|mixed|scan|increment [mixed]\n"
      "  -putPercent <put_percent> [100]\n"
      "  -createTable true|false [false]\n"
      "  -family <family> [f]\n"
      "  -numFamilies <numfamilies> [1]\n"
      "  -column <column> [c]\n"
      "  -numColumns <numcolumns> [1]\n"
      "  -startRow <start_row> [1]\n"
      "  -recordCount <number_of_records> [1000000]\n"
      "  -numOps <numops> [1000000]\n"
      "  -keyPrefix <key_prefix> [user]\n"
      "  -keyDistribution uniform|zipfian [uniform]\n"
      "  -hashKeys true|false [true]\n"
      "  -valueSize <value_size> [1024]\n"
      "  -bufferPuts true|false [true]\n"
      "  -writeToWAL true|false [true]\n"
      "  -flushBatchSize <flush_batch_size> [0(disabled)]\n"
      "  -maxPendingRPCs <max_pending_rpcs> [100000]\n"
      "  -numThreads <num_threads> [1]\n"
      "  -logFilePath <log_file_path>|disabled [stderr]\n"
      "  -maxRowsPerScan <max_rows_per_scan> [50]\n"
      "  -resumeThreshold <thread_resume_threshold> [20000]\n");
  exit(1);
}

static void
parseArgs(int argc,
          char *argv[]) {
  // skip program name
  argc--; argv++;
#define ArgEQ(a) (argc > 1 && (strcmp(argv[0], a) == 0))
  while (argc >= 1) {
    if (ArgEQ("-valueSize")) {
      options.valueLen_ = atoi(argv[1]);
    } else if (ArgEQ("-recordCount")) {
      argRecordCount = atol(argv[1]);
    } else if (ArgEQ("-numOps")) {
      options.numOps_ = atol(argv[1]);
    } else if (ArgEQ("-startRow")) {
      options.startRow_ = atol(argv[1]);
    } else if (ArgEQ("-table")) {
      argTableName = argv[1];
    } else if (ArgEQ("-family")) {
      argFamilyName = argv[1];
    } else if (ArgEQ("-numFamilies")) {
      options.numFamilies_ = atol(argv[1]);
    } else if (ArgEQ("-column")) {
      argColumnName = argv[1];
    } else if (ArgEQ("-numColumns")) {
      options.numColumns_ = atol(argv[1]);
    } else if (ArgEQ("-keyPrefix")) {
      options.keyPrefix_ = (byte_t *)argv[1];
    } else if (ArgEQ("-keyDistribution")) {
      if (strcmp(argv[1], "uniform") == 0) {
        argKeyDistribution = Uniform;
      } else if (strcmp(argv[1], "zipfian") == 0) {
        argKeyDistribution = Zipfian;
      } else if (strcmp(argv[1], "sequential") == 0) {
        argKeyDistribution = Sequential;
      } else {
        fprintf(stderr, "Invalid key distribution '%s'.", argv[1]);
        exit(1);
      }
    } else if (ArgEQ("-createTable")) {
      argCreateTable = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-checkRead")) {
      options.checkRead_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-workload")) {
      if (strcmp(argv[1], "load") == 0) {
        options.workload_ = Load;
      } else if (strcmp(argv[1], "mixed") == 0) {
        options.workload_ = Mixed;
      } else if (strcmp(argv[1], "scan") == 0) {
        options.workload_ = Scan;
      } else if (strcmp(argv[1], "increment") == 0) {
#ifdef HAS_INCREMENT_SUPPORT
        options.workload_ = Increment;
#else
        fprintf(stderr, "Increment operations are not yet implemented.");
        exit(1);
#endif
      } else {
        fprintf(stderr, "Invalid workload '%s'.", argv[1]);
        exit(1);
      }
    } else if (ArgEQ("-hashKeys")) {
      options.hashKeys_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-bufferPuts")) {
      options.bufferPuts_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-writeToWAL")) {
      options.writeToWAL_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-printKeys")) {
      options.printKeys_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-resendNoBufs")) {
      options.resendNoBufs_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-staticValues")) {
      options.staticValues_ = !(strcmp(argv[1], "false") == 0);
    } else if (ArgEQ("-zkQuorum")) {
      argZkQuorum = argv[1];
    } else if (ArgEQ("-zkRootNode")) {
      argZkRootNode = argv[1];
    } else if (ArgEQ("-logFilePath")) {
      if ((strcmp(argv[1], "disabled") == 0)) {
        argLogDisabled = true;
      } else {
        argLogFilePath = argv[1];
      }
    } else if (ArgEQ("-flushBatchSize")) {
      // if not set to 0, starts a thread to
      // flush after every 'flushBatchSize' RPCs
      options.flushBatchSize_ = atoi(argv[1]);
    } else if (ArgEQ("-maxPendingRPCs")) {
      argMaxPendingRPCs = atoi(argv[1]);
    } else if (ArgEQ("-numThreads")) {
      options.numThreads_ = atoi(argv[1]);
    } else if (ArgEQ("-putPercent")) {
      options.putPercent_ = atoi(argv[1]);
    } else if (ArgEQ("-maxRowsPerScan")) {
      options.maxRowsPerScan_ = atoi(argv[1]);
    } else if (ArgEQ("-resumeThreshold")) {
      options.resumeThreshold_ = atoi(argv[1]);
    } else {
      usage();
    }
    argv += 2; argc -= 2;
  }
#undef ArgEQ

  if (!options.bufferPuts_ && options.flushBatchSize_ > 0) {
    fprintf(stderr, "'-flushBatchSize' can not be specified if '-bufferPuts' is false");
  } else if (options.putPercent_ < 0 || options.putPercent_ > 100) {
    fprintf(stderr, "'-putPercent' must be between 0 and 100.");
  } else {
    // everything okay
    return;
  }
  exit(1);
}

void
trap_sigint(int signum) {
  HBASE_LOG_INFO("SIGINT detected, terminating test.");
  options.stopTest_ = true;
}

/**
 * Program entry point
 */
extern "C" int
main(int argc,
    char *argv[]) {
  if (argc == 1) usage();

  int32_t retCode = 0;
  FILE *logFile = NULL;
  hb_connection_t connection = NULL;
  hb_client_t client = NULL;
  KeyGenerator *keyGenerator = NULL;

  parseArgs(argc, argv);
  srand(time(NULL));

  options.maxPendingRPCsPerThread_ = argMaxPendingRPCs/options.numThreads_;
  if (options.maxPendingRPCsPerThread_ > SEM_VALUE_MAX) {
    fprintf(stderr, "Can not have more than %d pending RPCs per thread.",
            SEM_VALUE_MAX);
    exit(1);
  }

  options.table_ = bytebuffer_strcpy(argTableName);

  options.families_ = new bytebuffer[options.numFamilies_];
  if (options.numFamilies_ == 1) {
    options.families_[0] = bytebuffer_strcpy(argFamilyName);
  } else {
    for (uint32_t i = 0; i < options.numFamilies_; ++i) {
      char tmpFamily[1024] = {0};
      snprintf(tmpFamily, 1024, "%s%u", argFamilyName, i);
      options.families_[i] = bytebuffer_strcpy(tmpFamily);
    }
  }

  options.columns_ = new bytebuffer[options.numColumns_];
  if (options.numColumns_ == 1) {
    options.columns_[0] = bytebuffer_strcpy(argColumnName);
  } else {
    for (uint32_t i = 0; i < options.numColumns_; ++i) {
      char tmpColumn[1024] = {0};
      snprintf(tmpColumn, 1024, "%s%u", argColumnName, i);
      options.columns_[i] = bytebuffer_strcpy(tmpColumn);
    }
  }

  if (argLogDisabled) {
    hb_log_set_level(HBASE_LOG_LEVEL_FATAL);
  } else {
    hb_log_set_level(HBASE_LOG_LEVEL_DEBUG); // defaults to INFO
    if (argLogFilePath != NULL) {
      logFile = fopen(argLogFilePath, "a");
      if (!logFile) {
        retCode = errno;
        fprintf(stderr, "Unable to open log file \"%s\"", argLogFilePath);
        perror(NULL);
        goto cleanup;
      }
      hb_log_set_stream(logFile); // defaults to stderr
    }
  }

  HBASE_LOG_INFO("Starting perftest version " PERFTEST_VERSION_STR ".");

  if (argKeyDistribution == Sequential || options.workload_ == Load) {
    int64_t start = options.startRow_, stop = options.startRow_ + options.numOps_ - 1;
    HBASE_LOG_INFO("Using Sequential row keys between %lld and %lld.", start, stop);
    keyGenerator = (KeyGenerator *) new SequentialKeyGenerator(start, stop);
  } else {
    int64_t start = 0, stop = argRecordCount-1;
    switch (argKeyDistribution) {
    case Uniform:
      HBASE_LOG_INFO("Using Uniform distribution [%lld, %lld] for row keys.", start, stop);
      keyGenerator = (KeyGenerator *) new UniformKeyGenerator(start, stop);
      break;
    case Zipfian:
      HBASE_LOG_INFO("Using Zipfian distribution [%lld, %lld] for row keys.", start, stop);
      keyGenerator = (KeyGenerator *) new ScrambledZipfianGenerator(start, stop);
      break;
    default:
      assert(0);
      break;
    }
  }

  if ((retCode = hb_connection_create(argZkQuorum, argZkRootNode, &connection))) {
    HBASE_LOG_ERROR("Could not create HBase connection : errorCode = %d.", retCode);
    goto cleanup;
  }

  if ((retCode = ensureTable(connection,
      argCreateTable, argTableName, options.families_, options.numFamilies_)) != 0) {
    HBASE_LOG_ERROR("Failed to ensure table %s : errorCode = %d",
        argTableName, retCode);
    goto cleanup;
  }

  HBASE_LOG_INFO("Connecting to HBase cluster using Zookeeper ensemble '%s'.",
                 argZkQuorum ? argZkQuorum : "default");
  if ((retCode = hb_client_create(connection, &client)) != 0) {
    HBASE_LOG_ERROR("Could not connect to HBase cluster : errorCode = %d.", retCode);
    goto cleanup;
  }

  signal(SIGINT, trap_sigint);
  // launch threads
  {
    StatKeeper *statKeeper = new StatKeeper;
    Flusher *flushRunner = NULL;
    OpsRunner *runner[options.numThreads_];

    for (size_t id = 0; id < options.numThreads_; ++id) {
      runner[id] = new OpsRunner(id, client, &options, statKeeper, keyGenerator);
      runner[id]->Start();
    }

    statKeeper->Start();

    if (options.flushBatchSize_ > 0) {
      flushRunner = new Flusher(client, options.flushBatchSize_, statKeeper);
      flushRunner->Start();
    }

    for (size_t i = 0; i < options.numThreads_; ++i) {
      runner[i]->Stop();
      delete runner[i];
    }

    if (flushRunner != NULL) {
      flushRunner->Stop();
      delete flushRunner;
    }

    statKeeper->Stop();
    statKeeper->PrintSummary();
    delete statKeeper;
  }

cleanup:
  if (client) {
    disconnect_client_and_wait(client);
  }

  if (connection) {
    hb_connection_destroy(connection);
  }

  if (options.columns_) {
    for (uint32_t i = 0; i < options.numColumns_; ++i) {
      bytebuffer_free(options.columns_[i]);
    }

    delete [] options.columns_;
  }

  if (keyGenerator) {
    delete keyGenerator;
  }

  if (options.families_) {
    for (uint32_t i = 0; i < options.numFamilies_; ++i) {
      bytebuffer_free(options.families_[i]);
    }

    delete [] options.families_;
  }

  if (options.table_) {
    bytebuffer_free(options.table_);
  }

  if (logFile) {
    fclose(logFile);
  }

  return retCode;
}

} /* namespace test */
} /* namespace hbase */
