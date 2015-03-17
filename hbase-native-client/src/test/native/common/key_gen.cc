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

#include "key_gen.h"

#include <pthread.h>
#include <math.h>

#include <hbase/hbase.h>

#include "common_utils.h"
#include "test_types.h"

namespace hbase {
namespace test {

int32_t
KeyGenerator::NextInt32() {
  return (int32_t)NextInt64();
}

bytebuffer
KeyGenerator::NextRowKey(const char *prefix, const bool hashKeys) {
  int64_t nextNum = NextInt64();
  return bytebuffer_printf("%s%" PRIu64, prefix,
      (hashKeys ? FNVHash64(nextNum) : nextNum));
}


UniformKeyGenerator::UniformKeyGenerator(int lb, int ub)
{
  min_ = (lb < ub) ? lb : ub;
  max_ = (lb < ub) ? ub : lb;
  interval_ = max_ - min_ + 1;
}

int64_t
UniformKeyGenerator::NextInt64()
{
  return min_ + ((((int64_t)rand()) << 32) + rand()) % interval_;
}

/*
 * ZipfianGenerator
 */
const double ZipfianGenerator::ZIPFIAN_CONSTANT = 0.99;
const double ZipfianGenerator::ZETAN_CONSTANT = 26.46902820178302;

void
ZipfianGenerator::Init(int64_t min, int64_t max,
    double zipfianconstant, double zetan)
{
  items_ = max-min+1;
  base_ = min;
  zipfianconstant_ = zipfianconstant;
  theta_ = zipfianconstant_;
  zeta2theta_ = Zeta(2, theta_);
  alpha_ = 1.0/(1.0-theta_);
  zetan_ = zetan;
  countforzeta_ = items_;
  eta_ = (1-pow(2.0/items_,1-theta_))/(1-zeta2theta_/zetan_);
  allowitemcountdecrease_ = false;
  pthread_mutex_init(&lock_, 0);
}

int64_t
ZipfianGenerator::NextInt64()
{
  return NextInt64(items_);
}

int64_t
ZipfianGenerator::NextInt64(int64_t itemcount)
{
  //from "Quickly Generating Billion-Record Synthetic Databases", Jim Gray et al, SIGMOD 1994
  if (itemcount != countforzeta_) {
    //have to recompute zetan and eta, since they depend on itemcount
    pthread_mutex_lock(&lock_);
    {
      if (itemcount > countforzeta_) {
        zetan_ = Zeta(countforzeta_, itemcount, theta_, zetan_);
        eta_ = (1-pow(2.0/items_,1-theta_)) / (1-zeta2theta_/zetan_);
      } else if ((itemcount < countforzeta_) && (allowitemcountdecrease_)) {
        HBASE_LOG_WARN("Recomputing Zipfian distribution. "
            "This is slow and should be avoided. (itemcount = %" PRId64 ", countforzeta=%" PRId64 ").",
            itemcount, countforzeta_);
        zetan_ = Zeta(itemcount, theta_);
        eta_ = (1 - pow(2.0/items_, 1-theta_)) / (1 - zeta2theta_/zetan_);
      }
    }
  }
  pthread_mutex_unlock(&lock_);

  double u = random_double();
  double uz = u * zetan_;
  if (uz < 1.0) {
    return 0;
  } else if (uz < 1.0 + pow(0.5, theta_)) {
    return 1;
  }

  int64_t ret = base_ + (int64_t)((itemcount) * pow(eta_ * u - eta_ + 1, alpha_));
  return ret;
}

double
ZipfianGenerator::ZetaStatic(int64_t st, int64_t n, double theta, double initialsum)
{
  double sum = initialsum;
  for (int64_t i = st; i < n; ++i) {
    sum += 1 / (pow(i+1, theta));
  }
  return sum;
}

double
ZipfianGenerator::ZetaStatic(int64_t n, double theta)
{
  return ZetaStatic(0, n, theta, 0);
}

double
ZipfianGenerator::Zeta(int64_t n, double theta)
{
  countforzeta_ = n;
  return ZetaStatic(n, theta);
}

double
ZipfianGenerator::Zeta(int64_t st, int64_t n, double theta, double initialsum)
{
  countforzeta_ = n;
  return ZetaStatic(st, n, theta, initialsum);
}

ScrambledZipfianGenerator::ScrambledZipfianGenerator(int64_t lb,
                                                     int64_t ub)
{
  min_ = (lb < ub) ? lb : ub;
  max_ = (lb < ub) ? ub : lb;
  interval_ = max_ - min_ + 1;
  gen = new ZipfianGenerator(0, ITEM_COUNT,
                             ZipfianGenerator::ZIPFIAN_CONSTANT,
                             ZipfianGenerator::ZETAN_CONSTANT);
}

ScrambledZipfianGenerator::~ScrambledZipfianGenerator()
{
  delete gen;
}

int64_t
ScrambledZipfianGenerator::NextInt64()
{
  int64_t ret = gen->NextInt64();
  return min_ + (FNVHash64(ret) % interval_);
}

} /* namespace test */
} /* namespace hbase */
