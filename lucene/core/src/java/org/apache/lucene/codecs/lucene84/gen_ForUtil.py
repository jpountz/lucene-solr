#! /usr/bin/env python

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from fractions import gcd

"""Code generation for ForUtil.java"""

MAX_SPECIALIZED_BITS_PER_VALUE = 24;
OUTPUT_FILE = "ForUtil.java"
HEADER = """// This file has been automatically generated, DO NOT EDIT

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.codecs.lucene84;

import java.io.IOException;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.packed.PackedInts;

// Inspired from https://fulmicoton.com/posts/bitpacking/
// Encodes 2 integers at once by packing them in a long.
final class ForUtil {

  static final int BLOCK_SIZE = 128;
  private static final int BLOCK_SIZE_LOG2 = 7;
  static final int BLOCK_SIZE_IN_LONGS = 128 / 2;

  private static int numBitsPerValue(IntArray ints) {
    long or = 0;
    for (long l : ints.longs) {
      or |= l;
    }
    return PackedInts.bitsRequired((or | (or >>> 32)) & 0xFFFFFFFFL);
  }

  private static long expandMask(long mask32) {
    return mask32 | (mask32 << 32);
  }

  private static long mask(int bitsPerValue) {
    return expandMask((1L << bitsPerValue) - 1);
  }

  /**
   * Encode 128 32-bits integers from {@code data} into {@code out}.
   */
  void encode(IntArray ints, DataOutput out) throws IOException {
    final int bitsPerValue = numBitsPerValue(ints);
    out.writeByte((byte) bitsPerValue);

    long[] intPairs = ints.longs;
    int idx = 0;
    long nextBlock = 0;
    int bitsLeft = 32;
    for (int i = 0; i < BLOCK_SIZE_IN_LONGS; ++i) {
      bitsLeft -= bitsPerValue;
      if (bitsLeft > 0) {
        nextBlock |= intPairs[idx]++ << bitsLeft;
      } else if (bitsLeft == 0) {
        nextBlock |= intPairs[idx]++;
        out.writeLong(nextBlock);
        nextBlock = 0;
        bitsLeft = 32;
      } else {
        final long intPair = intPairs[idx]++;
        nextBlock |= (intPair >>> -bitsLeft) & mask(bitsPerValue + bitsLeft);
        out.writeLong(nextBlock);
        nextBlock = (intPair & mask(-bitsLeft)) << (32 + bitsLeft);
        bitsLeft += 32;
      }
    }
    assert bitsLeft == 32 : bitsLeft + " " + bitsPerValue;
  }

  /**
   * Skip 128 integers.
   */
  void skip(DataInput in) throws IOException {
    final int bitsPerValue = in.readByte();
    final int numBytes = bitsPerValue << (BLOCK_SIZE_LOG2 - 3);
    in.skipBytes(numBytes);
  }

  private static void decodeSlow(int bitsPerValue, DataInput in, long[] intPairs) throws IOException {
    final long mask = mask(bitsPerValue);
    long current = in.readLong();
    int bitsLeft = 32;
    int idx = 0;
    for (int i = 0; i < BLOCK_SIZE_IN_LONGS; ++i) {
      bitsLeft -= bitsPerValue;
      if (bitsLeft < 0) {
        long next = in.readLong();
        intPairs[idx++] = ((current & mask(bitsPerValue + bitsLeft)) << -bitsLeft) | ((next >>> (32 + bitsLeft)) & mask(-bitsLeft));
        current = next;
        bitsLeft += 32;
      } else {
        intPairs[idx++] = (current >>> bitsLeft) & mask;
      }
    }
  }

"""

def writeDecode(bpv, f):
  f.write('  private static void decode%d(DataInput in, long[] intPairs) throws IOException {\n' %bpv)
  num_values_per_iter = 128
  num_longs_per_iter = num_values_per_iter * bpv / 64
  while num_values_per_iter % 2 == 0 and num_longs_per_iter % 2 == 0:
    num_values_per_iter /= 2
    num_longs_per_iter /= 2
  f.write('    int idx = 0;\n')
  f.write('    for (int k = 0; k < %d; ++k) {\n' %(128 / num_values_per_iter))
  bits_left = 32
  for i in range(num_longs_per_iter):
    f.write('      long block%d = in.readLong();\n' %i)
    if bits_left > 32:
      prev_block_bits = bits_left - 32
      f.write('      intPairs[idx++] = ((block%d & MASK_%d) << %d) | ((block%d >>> %d) & MASK_%d);\n' %(i-1, prev_block_bits, bpv-prev_block_bits, i, 32-bpv+prev_block_bits, bpv-prev_block_bits))
      bits_left -= bpv
    if bits_left-2*bpv >= 0:
      f.write('      for (int shift = %d; shift >= 0; shift -= %d) {\n' %(bits_left-bpv,bpv))
      f.write('        intPairs[idx++] = (block%d >>> shift) & MASK_%d;\n' %(i, bpv))
      f.write('      }\n')
    else:
      # manually unroll
      for shift in range(bits_left-bpv, -1, -bpv):
        if shift == 0:
          f.write('      intPairs[idx++] = block%d & MASK_%d;\n' %(i, bpv))
        else:
          f.write('      intPairs[idx++] = (block%d >>> %d) & MASK_%d;\n' %(i, shift, bpv))

    bits_left = 32 + (bits_left % bpv)

  f.write('    }\n')
  f.write('  }\n')
  f.write('\n')

if __name__ == '__main__':
  f = open(OUTPUT_FILE, 'w')
  f.write(HEADER)
  for bpv in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    f.write('  private static final long MASK_%d = mask(%d);\n' %(bpv, bpv))
  f.write('\n')
  f.write("""
  /**
   * Decode 128 integers into {@code ints}.
   */
  void decode(DataInput in, IntArray ints) throws IOException {
    final int bitsPerValue = in.readByte();
    switch (bitsPerValue) {
""")
  for i in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    f.write('    case %d:\n' %i)
    f.write('      decode%d(in, ints.longs);\n' %i)
    f.write('      break;\n')
  f.write('    default:\n')
  f.write('      decodeSlow(bitsPerValue, in, ints.longs);\n')
  f.write('      break;\n')
  f.write('    }\n')
  f.write('  }\n')

  f.write('\n')
  for i in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    writeDecode(i, f)

  f.write('}\n')
