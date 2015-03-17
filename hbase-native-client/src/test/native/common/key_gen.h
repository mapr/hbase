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
#ifndef TEST_NATIVE_COMMON_KEY_GEN_H_
#define TEST_NATIVE_COMMON_KEY_GEN_H_

#include "test_types.h"

namespace hbase {
namespace test {

class KeyGenerator {
public:
  virtual ~KeyGenerator() {}
  virtual int64_t NextInt64() = 0;

  int32_t NextInt32();
  bytebuffer NextRowKey(const char *prefix, const bool hashKeys);
};

class UniformKeyGenerator : public KeyGenerator {
public:
  UniformKeyGenerator(int lb, int ub);

  virtual int64_t NextInt64();

private:
  int64_t min_, max_, interval_;
};

class ZipfianGenerator : public KeyGenerator {
public:
  static const double ZIPFIAN_CONSTANT;
  static const double ZETAN_CONSTANT;

  ZipfianGenerator(int64_t min, int64_t max) {
    Init(min, max, ZIPFIAN_CONSTANT, ZetaStatic(max-min+1, ZIPFIAN_CONSTANT));
  }

  ZipfianGenerator(int64_t min, int64_t max, double zipfianconstant, double zetan) {
    Init(min, max, zipfianconstant, zetan);
  }

  virtual int64_t NextInt64();

private:

  /* Number of items.*/
  int64_t items_;

  /* Min item to generate. */
  int64_t base_;

  /* The zipfian constant to use. */
  double zipfianconstant_;

  /* Computed parameters for generating the distribution. */
  double alpha_, zetan_, eta_, theta_, zeta2theta_;

  /* The number of items used to compute zetan the last time. */
  int64_t countforzeta_;

  bool allowitemcountdecrease_;

  pthread_mutex_t lock_;

  int64_t NextInt64(int64_t itemcount);

  double Zeta(int64_t n, double theta);
  double Zeta(int64_t st, int64_t n, double theta, double initialsum);
  void Init(int64_t min, int64_t max, double zipfianconstant, double zetan);

  static double ZetaStatic(int64_t n, double theta);
  static double ZetaStatic(int64_t st, int64_t n, double theta, double initialsum);
};

class ScrambledZipfianGenerator : public KeyGenerator {
public:
  ScrambledZipfianGenerator(int64_t lb, int64_t ub);
  ~ScrambledZipfianGenerator();
  int64_t NextInt64();

private:
  static const int64_t ITEM_COUNT = 10000000000L;

  int64_t min_, max_, interval_;
  ZipfianGenerator *gen;
};

} /* namespace test */
} /* namespace hbase */

#endif /* TEST_NATIVE_COMMON_KEY_GEN_H_ */
