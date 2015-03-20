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
#line 19 "key_gen.cc" // ensures short filename in logs.

#include "key_gen.h"

#include <pthread.h>
#include <math.h>

#include <hbase/hbase.h>

#include "common_utils.h"
#include "test_types.h"

namespace hbase {
namespace test {

/* KeyGenerator */

KeyGenerator::KeyGenerator(int64_t items)
{
  Init(0, items-1);
}

KeyGenerator::KeyGenerator(int64_t lb, int64_t ub)
{
  Init(lb, ub);
}

void
KeyGenerator::Init(int64_t lb, int64_t ub)
{
  min_ = (lb < ub) ? lb : ub;
  max_ = (lb < ub) ? ub : lb;
  items_ = max_ - min_ + 1;
}

int32_t
KeyGenerator::NextInt32(Random *random)
{
  return (int32_t)NextInt64(random);
}

bytebuffer
KeyGenerator::NextRowKey(Random *random,
    const unsigned char *prefix, const bool hashKeys)
{
  int64_t nextNum = NextInt64(random);
  return bytebuffer_printf("%s%" PRIu64, prefix,
      (hashKeys ? FNVHash64(nextNum) : nextNum));
}

/* SequentialKeyGenerator */

SequentialKeyGenerator::SequentialKeyGenerator(
    int64_t lb, int64_t ub) : KeyGenerator(lb, ub)
{
  Init();
}

SequentialKeyGenerator::SequentialKeyGenerator(
    int64_t items) : KeyGenerator(0, items-1)
{
  Init();
}

void
SequentialKeyGenerator::Init()
{
  lastNum_ = min_;
  pthread_mutex_init(&lock_, 0);
}

int64_t
SequentialKeyGenerator::NextInt64(Random */*ignored*/)
{
  int64_t nextNum = atomic_add64(&lastNum_, 1);
  if (nextNum > max_) {
    // reset and roll over
    pthread_mutex_lock(&lock_);
    {
      if (lastNum_ > max_) {
        lastNum_ = min_;
      }
      if (nextNum > max_) {
        nextNum = atomic_add64(&lastNum_, 1);
      }
    }
    pthread_mutex_unlock(&lock_);
  }
  return nextNum;
}

/* UniformKeyGenerator */

UniformKeyGenerator::UniformKeyGenerator(int64_t lb,
    int64_t ub) : KeyGenerator(lb, ub)
{
}

int64_t
UniformKeyGenerator::NextInt64(Random *random)
{
  /*
   * Since RandomInt64 generates random int64_t which can be negative, we
   * shift the bits to the left and back by 1 bit to introduce the MSB as zero.
   */
  return min_ + ((((uint64_t)(RandomInt64(random) << 1)) >> 1) % items_);
}

/*
 * ZipfianGenerator
 */
const double ZipfianGenerator::ZIPFIAN_CONSTANT = 0.99;
const double ZipfianGenerator::ZETAN_CONSTANT = 26.46902820178302;

ZipfianGenerator::ZipfianGenerator(int64_t lb, int64_t ub)
  : KeyGenerator(lb, ub)
{
  Init(ZIPFIAN_CONSTANT, ZetaStatic(items_, ZIPFIAN_CONSTANT));
}

ZipfianGenerator::ZipfianGenerator(int64_t lb, int64_t ub,
    double zipfianconstant, double zetan) : KeyGenerator(lb, ub)
{
  Init(zipfianconstant, zetan);
}

void
ZipfianGenerator::Init(double zipfianconstant, double zetan)
{
  base_ = min_;
  zipfianconstant_ = zipfianconstant;
  theta_ = zipfianconstant_;
  zeta2theta_ = Zeta(2, theta_);
  alpha_ = 1.0/(1.0-theta_);
  zetan_ = zetan;
  countforzeta_ = items_;
  eta_ = (1-pow(2.0/items_, 1-theta_))/(1-zeta2theta_/zetan_);
  allowitemcountdecrease_ = false;
  pthread_mutex_init(&lock_, 0);
}

int64_t
ZipfianGenerator::NextInt64(Random *random)
{
  return NextInt64(random, items_);
}

int64_t
ZipfianGenerator::NextInt64(Random *random, int64_t itemcount)
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
    pthread_mutex_unlock(&lock_);
  }

  double u = RandomDouble(random);
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

ScrambledZipfianGenerator::ScrambledZipfianGenerator(
    int64_t lb, int64_t ub) : KeyGenerator(lb, ub)
{
  gen = new ZipfianGenerator(0, ITEM_COUNT,
                             ZipfianGenerator::ZIPFIAN_CONSTANT,
                             ZipfianGenerator::ZETAN_CONSTANT);
}

ScrambledZipfianGenerator::~ScrambledZipfianGenerator()
{
  delete gen;
}

int64_t
ScrambledZipfianGenerator::NextInt64(Random *random)
{
  int64_t ret = gen->NextInt64(random);
  return min_ + (FNVHash64(ret) % items_);
}

} /* namespace test */
} /* namespace hbase */
