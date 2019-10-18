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
import java.util.function.IntToLongFunction;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.packed.PackedInts;

// Inspired from https://fulmicoton.com/posts/bitpacking/
// Encodes multiple integers in a long to get SIMD-like speedups.
// If bitsPerValue <= 8 then we pack 8 ints per long
// else if bitsPerValue <= 16 we pack 4 ints per long
// else we pack 2 ints per long
final class ForUtil {

  static final int BLOCK_SIZE = 128;
  private static final int BLOCK_SIZE_LOG2 = 7;

  private static int numBitsPerValue(long[] longs) {
    long or = 0;
    for (long l : longs) {
      or |= l;
    }
    return PackedInts.bitsRequired(or);
  }

  private static long expandMask32(long mask32) {
    return mask32 | (mask32 << 32);
  }

  private static long expandMask16(long mask16) {
    return expandMask32(mask16 | (mask16 << 16));
  }

  private static long expandMask8(long mask8) {
    return expandMask16(mask8 | (mask8 << 8));
  }

  private static long mask32(int bitsPerValue) {
    return expandMask32((1L << bitsPerValue) - 1);
  }

  private static long mask16(int bitsPerValue) {
    return expandMask16((1L << bitsPerValue) - 1);
  }

  private static long mask8(int bitsPerValue) {
    return expandMask8((1L << bitsPerValue) - 1);
  }

  private static void expand8(long[] arr) {
    for (int i = 0; i < 16; ++i) {
      long l = arr[i];
      arr[i] = (l >>> 56) & 0xFFL;
      arr[16+i] = (l >>> 48) & 0xFFL;
      arr[32+i] = (l >>> 40) & 0xFFL;
      arr[48+i] = (l >>> 32) & 0xFFL;
      arr[64+i] = (l >>> 24) & 0xFFL;
      arr[80+i] = (l >>> 16) & 0xFFL;
      arr[96+i] = (l >>> 8) & 0xFFL;
      arr[112+i] = l & 0xFFL;
    }
  }

  private static void collapse8(long[] arr) {
    for (int i = 0; i < 16; ++i) {
      arr[i] = (arr[i] << 56) | (arr[16+i] << 48) | (arr[32+i] << 40) | (arr[48+i] << 32) | (arr[64+i] << 24) | (arr[80+i] << 16) | (arr[96+i] << 8) | arr[112+i];
    }
  }

  private static void expand16(long[] arr) {
    for (int i = 0; i < 32; ++i) {
      long l = arr[i];
      arr[i] = (l >>> 48) & 0xFFFFL;
      arr[32+i] = (l >>> 32) & 0xFFFFL;
      arr[64+i] = (l >>> 16) & 0xFFFFL;
      arr[96+i] = l & 0xFFFFL;
    }
  }

  private static void collapse16(long[] arr) {
    for (int i = 0; i < 32; ++i) {
      arr[i] = (arr[i] << 48) | (arr[32+i] << 32) | (arr[64+i] << 16) | arr[96+i];
    }
  }

  private static void expand32(long[] arr) {
    for (int i = 0; i < 64; ++i) {
      long l = arr[i];
      arr[i] = l >>> 32;
      arr[64 + i] = l & 0xFFFFFFFFL;
    }
  }

  private static void collapse32(long[] arr) {
    for (int i = 0; i < 64; ++i) {
      arr[i] = (arr[i] << 32) | arr[64+i];
    }
  }

  /**
   * Encode 128 8-bits integers from {@code data} into {@code out}.
   */
  void encode(long[] longs, DataOutput out) throws IOException {
    final int bitsPerValue = numBitsPerValue(longs);
    out.writeByte((byte) bitsPerValue);

    final int nextPrimitive;
    final int numLongs;
    final IntToLongFunction maskFunction;
    if (bitsPerValue <= 8) {
      nextPrimitive = 8;
      numLongs = BLOCK_SIZE / 8;
      maskFunction = ForUtil::mask8;
      collapse8(longs);
    } else if (bitsPerValue <= 16) {
      nextPrimitive = 16;
      numLongs = BLOCK_SIZE / 4;
      maskFunction = ForUtil::mask16;
      collapse16(longs);
    } else {
      nextPrimitive = 32;
      numLongs = BLOCK_SIZE / 2;
      maskFunction = ForUtil::mask32;
      collapse32(longs);
    }

    int idx = 0;
    long nextBlock = 0;
    int bitsLeft = nextPrimitive;
    for (int i = 0; i < numLongs; ++i) {
      bitsLeft -= bitsPerValue;
      if (bitsLeft > 0) {
        nextBlock |= longs[idx++] << bitsLeft;
      } else if (bitsLeft == 0) {
        nextBlock |= longs[idx++];
        out.writeLong(nextBlock);
        nextBlock = 0;
        bitsLeft = nextPrimitive;
      } else {
        final long l = longs[idx++];
        nextBlock |= (l >>> -bitsLeft) & maskFunction.applyAsLong(bitsPerValue + bitsLeft);
        out.writeLong(nextBlock);
        nextBlock = (l & maskFunction.applyAsLong(-bitsLeft)) << (nextPrimitive + bitsLeft);
        bitsLeft += nextPrimitive;
      }
    }
    assert bitsLeft == nextPrimitive;
  }

  /**
   * Skip 128 integers.
   */
  void skip(DataInput in) throws IOException {
    final int bitsPerValue = in.readByte();
    final int numBytes = bitsPerValue << (BLOCK_SIZE_LOG2 - 3);
    in.skipBytes(numBytes);
  }

  private static void decodeSlow(int bitsPerValue, DataInput in, long[] longs) throws IOException {
    final long mask = mask32(bitsPerValue);
    long current = in.readLong();
    int bitsLeft = 32;
    int idx = 0;
    for (int i = 0; i < BLOCK_SIZE / 2; ++i) {
      bitsLeft -= bitsPerValue;
      if (bitsLeft < 0) {
        long next = in.readLong();
        longs[idx++] = ((current & mask32(bitsPerValue + bitsLeft)) << -bitsLeft) | ((next >>> (32 + bitsLeft)) & mask32(-bitsLeft));
        current = next;
        bitsLeft += 32;
      } else {
        longs[idx++] = (current >>> bitsLeft) & mask;
      }
    }
    expand32(longs);
  }

"""

def writeDecode(bpv, f):
  next_primitive = 32
  if bpv <= 8:
    next_primitive = 8
  elif bpv <= 16:
    next_primitive = 16
  f.write('  private static void decode%d(DataInput in, long[] longs) throws IOException {\n' %bpv)
  num_values_per_iter = 128
  num_longs_per_iter = num_values_per_iter * bpv / 64
  while num_values_per_iter % 2 == 0 and num_longs_per_iter % 2 == 0:
    num_values_per_iter /= 2
    num_longs_per_iter /= 2
  f.write('    int idx = 0;\n')
  f.write('    for (int k = 0; k < %d; ++k) {\n' %(128 / num_values_per_iter))
  bits_left = next_primitive
  for i in range(num_longs_per_iter):
    f.write('      long block%d = in.readLong();\n' %i)
    if bits_left > next_primitive:
      prev_block_bits = bits_left - next_primitive
      f.write('      longs[idx++] = ((block%d & MASK%d_%d) << %d) | ((block%d >>> %d) & MASK%d_%d);\n' %(i-1, next_primitive, prev_block_bits, bpv-prev_block_bits, i, next_primitive-bpv+prev_block_bits, next_primitive, bpv-prev_block_bits))
      bits_left -= bpv
    if bits_left-2*bpv >= 0:
      f.write('      for (int shift = %d; shift >= 0; shift -= %d) {\n' %(bits_left-bpv,bpv))
      f.write('        longs[idx++] = (block%d >>> shift) & MASK%d_%d;\n' %(i, next_primitive, bpv))
      f.write('      }\n')
    else:
      # manually unroll
      for shift in range(bits_left-bpv, -1, -bpv):
        if shift == 0:
          f.write('      longs[idx++] = block%d & MASK%d_%d;\n' %(i, next_primitive, bpv))
        else:
          f.write('      longs[idx++] = (block%d >>> %d) & MASK%d_%d;\n' %(i, shift, next_primitive, bpv))

    bits_left = next_primitive + (bits_left % bpv)

  f.write('    }\n')
  f.write('    expand%d(longs);\n' %next_primitive)
  f.write('  }\n')
  f.write('\n')

if __name__ == '__main__':
  f = open(OUTPUT_FILE, 'w')
  f.write(HEADER)
  for primitive_size in [8, 16, 32]:
    for bpv in range(1, min(MAX_SPECIALIZED_BITS_PER_VALUE, primitive_size)+1):
      f.write('  private static final long MASK%d_%d = mask%d(%d);\n' %(primitive_size, bpv, primitive_size, bpv))
  f.write('\n')
  f.write("""
  /**
   * Decode 128 integers into {@code ints}.
   */
  void decode(DataInput in, long[] longs) throws IOException {
    final int bitsPerValue = in.readByte();
    switch (bitsPerValue) {
""")
  for i in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    f.write('    case %d:\n' %i)
    f.write('      decode%d(in, longs);\n' %i)
    f.write('      break;\n')
  f.write('    default:\n')
  f.write('      decodeSlow(bitsPerValue, in, longs);\n')
  f.write('      break;\n')
  f.write('    }\n')
  f.write('  }\n')

  f.write('\n')
  for i in range(1, MAX_SPECIALIZED_BITS_PER_VALUE+1):
    writeDecode(i, f)

  f.write('}\n')
