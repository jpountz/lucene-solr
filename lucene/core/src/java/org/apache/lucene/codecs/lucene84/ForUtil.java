// This file has been automatically generated, DO NOT EDIT

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

  private static final long MASK8_1 = mask8(1);
  private static final long MASK8_2 = mask8(2);
  private static final long MASK8_3 = mask8(3);
  private static final long MASK8_4 = mask8(4);
  private static final long MASK8_5 = mask8(5);
  private static final long MASK8_6 = mask8(6);
  private static final long MASK8_7 = mask8(7);
  private static final long MASK8_8 = mask8(8);
  private static final long MASK16_1 = mask16(1);
  private static final long MASK16_2 = mask16(2);
  private static final long MASK16_3 = mask16(3);
  private static final long MASK16_4 = mask16(4);
  private static final long MASK16_5 = mask16(5);
  private static final long MASK16_6 = mask16(6);
  private static final long MASK16_7 = mask16(7);
  private static final long MASK16_8 = mask16(8);
  private static final long MASK16_9 = mask16(9);
  private static final long MASK16_10 = mask16(10);
  private static final long MASK16_11 = mask16(11);
  private static final long MASK16_12 = mask16(12);
  private static final long MASK16_13 = mask16(13);
  private static final long MASK16_14 = mask16(14);
  private static final long MASK16_15 = mask16(15);
  private static final long MASK16_16 = mask16(16);
  private static final long MASK32_1 = mask32(1);
  private static final long MASK32_2 = mask32(2);
  private static final long MASK32_3 = mask32(3);
  private static final long MASK32_4 = mask32(4);
  private static final long MASK32_5 = mask32(5);
  private static final long MASK32_6 = mask32(6);
  private static final long MASK32_7 = mask32(7);
  private static final long MASK32_8 = mask32(8);
  private static final long MASK32_9 = mask32(9);
  private static final long MASK32_10 = mask32(10);
  private static final long MASK32_11 = mask32(11);
  private static final long MASK32_12 = mask32(12);
  private static final long MASK32_13 = mask32(13);
  private static final long MASK32_14 = mask32(14);
  private static final long MASK32_15 = mask32(15);
  private static final long MASK32_16 = mask32(16);
  private static final long MASK32_17 = mask32(17);
  private static final long MASK32_18 = mask32(18);
  private static final long MASK32_19 = mask32(19);
  private static final long MASK32_20 = mask32(20);
  private static final long MASK32_21 = mask32(21);
  private static final long MASK32_22 = mask32(22);
  private static final long MASK32_23 = mask32(23);
  private static final long MASK32_24 = mask32(24);


  /**
   * Decode 128 integers into {@code ints}.
   */
  void decode(DataInput in, long[] longs) throws IOException {
    final int bitsPerValue = in.readByte();
    switch (bitsPerValue) {
    case 1:
      decode1(in, longs);
      break;
    case 2:
      decode2(in, longs);
      break;
    case 3:
      decode3(in, longs);
      break;
    case 4:
      decode4(in, longs);
      break;
    case 5:
      decode5(in, longs);
      break;
    case 6:
      decode6(in, longs);
      break;
    case 7:
      decode7(in, longs);
      break;
    case 8:
      decode8(in, longs);
      break;
    case 9:
      decode9(in, longs);
      break;
    case 10:
      decode10(in, longs);
      break;
    case 11:
      decode11(in, longs);
      break;
    case 12:
      decode12(in, longs);
      break;
    case 13:
      decode13(in, longs);
      break;
    case 14:
      decode14(in, longs);
      break;
    case 15:
      decode15(in, longs);
      break;
    case 16:
      decode16(in, longs);
      break;
    case 17:
      decode17(in, longs);
      break;
    case 18:
      decode18(in, longs);
      break;
    case 19:
      decode19(in, longs);
      break;
    case 20:
      decode20(in, longs);
      break;
    case 21:
      decode21(in, longs);
      break;
    case 22:
      decode22(in, longs);
      break;
    case 23:
      decode23(in, longs);
      break;
    case 24:
      decode24(in, longs);
      break;
    default:
      decodeSlow(bitsPerValue, in, longs);
      break;
    }
  }

  private static void decode1(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 7; shift >= 0; shift -= 1) {
        longs[idx++] = (block0 >>> shift) & MASK8_1;
      }
    }
    expand8(longs);
  }

  private static void decode2(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 6; shift >= 0; shift -= 2) {
        longs[idx++] = (block0 >>> shift) & MASK8_2;
      }
    }
    expand8(longs);
  }

  private static void decode3(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 5; shift >= 0; shift -= 3) {
        longs[idx++] = (block0 >>> shift) & MASK8_3;
      }
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK8_2) << 1) | ((block1 >>> 7) & MASK8_1);
      for (int shift = 4; shift >= 0; shift -= 3) {
        longs[idx++] = (block1 >>> shift) & MASK8_3;
      }
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK8_1) << 2) | ((block2 >>> 6) & MASK8_2);
      for (int shift = 3; shift >= 0; shift -= 3) {
        longs[idx++] = (block2 >>> shift) & MASK8_3;
      }
    }
    expand8(longs);
  }

  private static void decode4(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      for (int shift = 4; shift >= 0; shift -= 4) {
        longs[idx++] = (block0 >>> shift) & MASK8_4;
      }
    }
    expand8(longs);
  }

  private static void decode5(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 3) & MASK8_5;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK8_3) << 2) | ((block1 >>> 6) & MASK8_2);
      longs[idx++] = (block1 >>> 1) & MASK8_5;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK8_1) << 4) | ((block2 >>> 4) & MASK8_4);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK8_4) << 1) | ((block3 >>> 7) & MASK8_1);
      longs[idx++] = (block3 >>> 2) & MASK8_5;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK8_2) << 3) | ((block4 >>> 5) & MASK8_3);
      longs[idx++] = block4 & MASK8_5;
    }
    expand8(longs);
  }

  private static void decode6(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 2) & MASK8_6;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK8_2) << 4) | ((block1 >>> 4) & MASK8_4);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK8_4) << 2) | ((block2 >>> 6) & MASK8_2);
      longs[idx++] = block2 & MASK8_6;
    }
    expand8(longs);
  }

  private static void decode7(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 1) & MASK8_7;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK8_1) << 6) | ((block1 >>> 2) & MASK8_6);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK8_2) << 5) | ((block2 >>> 3) & MASK8_5);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK8_3) << 4) | ((block3 >>> 4) & MASK8_4);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK8_4) << 3) | ((block4 >>> 5) & MASK8_3);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK8_5) << 2) | ((block5 >>> 6) & MASK8_2);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK8_6) << 1) | ((block6 >>> 7) & MASK8_1);
      longs[idx++] = block6 & MASK8_7;
    }
    expand8(longs);
  }

  private static void decode8(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 16; ++k) {
      long block0 = in.readLong();
      longs[idx++] = block0 & MASK8_8;
    }
    expand8(longs);
  }

  private static void decode9(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 7) & MASK16_9;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_7) << 2) | ((block1 >>> 14) & MASK16_2);
      longs[idx++] = (block1 >>> 5) & MASK16_9;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_5) << 4) | ((block2 >>> 12) & MASK16_4);
      longs[idx++] = (block2 >>> 3) & MASK16_9;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_3) << 6) | ((block3 >>> 10) & MASK16_6);
      longs[idx++] = (block3 >>> 1) & MASK16_9;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_1) << 8) | ((block4 >>> 8) & MASK16_8);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK16_8) << 1) | ((block5 >>> 15) & MASK16_1);
      longs[idx++] = (block5 >>> 6) & MASK16_9;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK16_6) << 3) | ((block6 >>> 13) & MASK16_3);
      longs[idx++] = (block6 >>> 4) & MASK16_9;
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK16_4) << 5) | ((block7 >>> 11) & MASK16_5);
      longs[idx++] = (block7 >>> 2) & MASK16_9;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK16_2) << 7) | ((block8 >>> 9) & MASK16_7);
      longs[idx++] = block8 & MASK16_9;
    }
    expand16(longs);
  }

  private static void decode10(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 6) & MASK16_10;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_6) << 4) | ((block1 >>> 12) & MASK16_4);
      longs[idx++] = (block1 >>> 2) & MASK16_10;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_2) << 8) | ((block2 >>> 8) & MASK16_8);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_8) << 2) | ((block3 >>> 14) & MASK16_2);
      longs[idx++] = (block3 >>> 4) & MASK16_10;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_4) << 6) | ((block4 >>> 10) & MASK16_6);
      longs[idx++] = block4 & MASK16_10;
    }
    expand16(longs);
  }

  private static void decode11(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 5) & MASK16_11;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_5) << 6) | ((block1 >>> 10) & MASK16_6);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_10) << 1) | ((block2 >>> 15) & MASK16_1);
      longs[idx++] = (block2 >>> 4) & MASK16_11;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_4) << 7) | ((block3 >>> 9) & MASK16_7);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_9) << 2) | ((block4 >>> 14) & MASK16_2);
      longs[idx++] = (block4 >>> 3) & MASK16_11;
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK16_3) << 8) | ((block5 >>> 8) & MASK16_8);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK16_8) << 3) | ((block6 >>> 13) & MASK16_3);
      longs[idx++] = (block6 >>> 2) & MASK16_11;
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK16_2) << 9) | ((block7 >>> 7) & MASK16_9);
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK16_7) << 4) | ((block8 >>> 12) & MASK16_4);
      longs[idx++] = (block8 >>> 1) & MASK16_11;
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK16_1) << 10) | ((block9 >>> 6) & MASK16_10);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK16_6) << 5) | ((block10 >>> 11) & MASK16_5);
      longs[idx++] = block10 & MASK16_11;
    }
    expand16(longs);
  }

  private static void decode12(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 4) & MASK16_12;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_4) << 8) | ((block1 >>> 8) & MASK16_8);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_8) << 4) | ((block2 >>> 12) & MASK16_4);
      longs[idx++] = block2 & MASK16_12;
    }
    expand16(longs);
  }

  private static void decode13(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 3) & MASK16_13;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_3) << 10) | ((block1 >>> 6) & MASK16_10);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_6) << 7) | ((block2 >>> 9) & MASK16_7);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_9) << 4) | ((block3 >>> 12) & MASK16_4);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_12) << 1) | ((block4 >>> 15) & MASK16_1);
      longs[idx++] = (block4 >>> 2) & MASK16_13;
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK16_2) << 11) | ((block5 >>> 5) & MASK16_11);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK16_5) << 8) | ((block6 >>> 8) & MASK16_8);
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK16_8) << 5) | ((block7 >>> 11) & MASK16_5);
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK16_11) << 2) | ((block8 >>> 14) & MASK16_2);
      longs[idx++] = (block8 >>> 1) & MASK16_13;
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK16_1) << 12) | ((block9 >>> 4) & MASK16_12);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK16_4) << 9) | ((block10 >>> 7) & MASK16_9);
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK16_7) << 6) | ((block11 >>> 10) & MASK16_6);
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK16_10) << 3) | ((block12 >>> 13) & MASK16_3);
      longs[idx++] = block12 & MASK16_13;
    }
    expand16(longs);
  }

  private static void decode14(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 2) & MASK16_14;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_2) << 12) | ((block1 >>> 4) & MASK16_12);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_4) << 10) | ((block2 >>> 6) & MASK16_10);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_6) << 8) | ((block3 >>> 8) & MASK16_8);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_8) << 6) | ((block4 >>> 10) & MASK16_6);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK16_10) << 4) | ((block5 >>> 12) & MASK16_4);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK16_12) << 2) | ((block6 >>> 14) & MASK16_2);
      longs[idx++] = block6 & MASK16_14;
    }
    expand16(longs);
  }

  private static void decode15(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 1) & MASK16_15;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK16_1) << 14) | ((block1 >>> 2) & MASK16_14);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK16_2) << 13) | ((block2 >>> 3) & MASK16_13);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK16_3) << 12) | ((block3 >>> 4) & MASK16_12);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK16_4) << 11) | ((block4 >>> 5) & MASK16_11);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK16_5) << 10) | ((block5 >>> 6) & MASK16_10);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK16_6) << 9) | ((block6 >>> 7) & MASK16_9);
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK16_7) << 8) | ((block7 >>> 8) & MASK16_8);
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK16_8) << 7) | ((block8 >>> 9) & MASK16_7);
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK16_9) << 6) | ((block9 >>> 10) & MASK16_6);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK16_10) << 5) | ((block10 >>> 11) & MASK16_5);
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK16_11) << 4) | ((block11 >>> 12) & MASK16_4);
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK16_12) << 3) | ((block12 >>> 13) & MASK16_3);
      long block13 = in.readLong();
      longs[idx++] = ((block12 & MASK16_13) << 2) | ((block13 >>> 14) & MASK16_2);
      long block14 = in.readLong();
      longs[idx++] = ((block13 & MASK16_14) << 1) | ((block14 >>> 15) & MASK16_1);
      longs[idx++] = block14 & MASK16_15;
    }
    expand16(longs);
  }

  private static void decode16(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 32; ++k) {
      long block0 = in.readLong();
      longs[idx++] = block0 & MASK16_16;
    }
    expand16(longs);
  }

  private static void decode17(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 15) & MASK32_17;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_15) << 2) | ((block1 >>> 30) & MASK32_2);
      longs[idx++] = (block1 >>> 13) & MASK32_17;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_13) << 4) | ((block2 >>> 28) & MASK32_4);
      longs[idx++] = (block2 >>> 11) & MASK32_17;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_11) << 6) | ((block3 >>> 26) & MASK32_6);
      longs[idx++] = (block3 >>> 9) & MASK32_17;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_9) << 8) | ((block4 >>> 24) & MASK32_8);
      longs[idx++] = (block4 >>> 7) & MASK32_17;
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_7) << 10) | ((block5 >>> 22) & MASK32_10);
      longs[idx++] = (block5 >>> 5) & MASK32_17;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_5) << 12) | ((block6 >>> 20) & MASK32_12);
      longs[idx++] = (block6 >>> 3) & MASK32_17;
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_3) << 14) | ((block7 >>> 18) & MASK32_14);
      longs[idx++] = (block7 >>> 1) & MASK32_17;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_1) << 16) | ((block8 >>> 16) & MASK32_16);
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK32_16) << 1) | ((block9 >>> 31) & MASK32_1);
      longs[idx++] = (block9 >>> 14) & MASK32_17;
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK32_14) << 3) | ((block10 >>> 29) & MASK32_3);
      longs[idx++] = (block10 >>> 12) & MASK32_17;
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK32_12) << 5) | ((block11 >>> 27) & MASK32_5);
      longs[idx++] = (block11 >>> 10) & MASK32_17;
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK32_10) << 7) | ((block12 >>> 25) & MASK32_7);
      longs[idx++] = (block12 >>> 8) & MASK32_17;
      long block13 = in.readLong();
      longs[idx++] = ((block12 & MASK32_8) << 9) | ((block13 >>> 23) & MASK32_9);
      longs[idx++] = (block13 >>> 6) & MASK32_17;
      long block14 = in.readLong();
      longs[idx++] = ((block13 & MASK32_6) << 11) | ((block14 >>> 21) & MASK32_11);
      longs[idx++] = (block14 >>> 4) & MASK32_17;
      long block15 = in.readLong();
      longs[idx++] = ((block14 & MASK32_4) << 13) | ((block15 >>> 19) & MASK32_13);
      longs[idx++] = (block15 >>> 2) & MASK32_17;
      long block16 = in.readLong();
      longs[idx++] = ((block15 & MASK32_2) << 15) | ((block16 >>> 17) & MASK32_15);
      longs[idx++] = block16 & MASK32_17;
    }
    expand32(longs);
  }

  private static void decode18(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 14) & MASK32_18;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_14) << 4) | ((block1 >>> 28) & MASK32_4);
      longs[idx++] = (block1 >>> 10) & MASK32_18;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_10) << 8) | ((block2 >>> 24) & MASK32_8);
      longs[idx++] = (block2 >>> 6) & MASK32_18;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_6) << 12) | ((block3 >>> 20) & MASK32_12);
      longs[idx++] = (block3 >>> 2) & MASK32_18;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_2) << 16) | ((block4 >>> 16) & MASK32_16);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_16) << 2) | ((block5 >>> 30) & MASK32_2);
      longs[idx++] = (block5 >>> 12) & MASK32_18;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_12) << 6) | ((block6 >>> 26) & MASK32_6);
      longs[idx++] = (block6 >>> 8) & MASK32_18;
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_8) << 10) | ((block7 >>> 22) & MASK32_10);
      longs[idx++] = (block7 >>> 4) & MASK32_18;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_4) << 14) | ((block8 >>> 18) & MASK32_14);
      longs[idx++] = block8 & MASK32_18;
    }
    expand32(longs);
  }

  private static void decode19(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 13) & MASK32_19;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_13) << 6) | ((block1 >>> 26) & MASK32_6);
      longs[idx++] = (block1 >>> 7) & MASK32_19;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_7) << 12) | ((block2 >>> 20) & MASK32_12);
      longs[idx++] = (block2 >>> 1) & MASK32_19;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_1) << 18) | ((block3 >>> 14) & MASK32_18);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_14) << 5) | ((block4 >>> 27) & MASK32_5);
      longs[idx++] = (block4 >>> 8) & MASK32_19;
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_8) << 11) | ((block5 >>> 21) & MASK32_11);
      longs[idx++] = (block5 >>> 2) & MASK32_19;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_2) << 17) | ((block6 >>> 15) & MASK32_17);
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_15) << 4) | ((block7 >>> 28) & MASK32_4);
      longs[idx++] = (block7 >>> 9) & MASK32_19;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_9) << 10) | ((block8 >>> 22) & MASK32_10);
      longs[idx++] = (block8 >>> 3) & MASK32_19;
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK32_3) << 16) | ((block9 >>> 16) & MASK32_16);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK32_16) << 3) | ((block10 >>> 29) & MASK32_3);
      longs[idx++] = (block10 >>> 10) & MASK32_19;
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK32_10) << 9) | ((block11 >>> 23) & MASK32_9);
      longs[idx++] = (block11 >>> 4) & MASK32_19;
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK32_4) << 15) | ((block12 >>> 17) & MASK32_15);
      long block13 = in.readLong();
      longs[idx++] = ((block12 & MASK32_17) << 2) | ((block13 >>> 30) & MASK32_2);
      longs[idx++] = (block13 >>> 11) & MASK32_19;
      long block14 = in.readLong();
      longs[idx++] = ((block13 & MASK32_11) << 8) | ((block14 >>> 24) & MASK32_8);
      longs[idx++] = (block14 >>> 5) & MASK32_19;
      long block15 = in.readLong();
      longs[idx++] = ((block14 & MASK32_5) << 14) | ((block15 >>> 18) & MASK32_14);
      long block16 = in.readLong();
      longs[idx++] = ((block15 & MASK32_18) << 1) | ((block16 >>> 31) & MASK32_1);
      longs[idx++] = (block16 >>> 12) & MASK32_19;
      long block17 = in.readLong();
      longs[idx++] = ((block16 & MASK32_12) << 7) | ((block17 >>> 25) & MASK32_7);
      longs[idx++] = (block17 >>> 6) & MASK32_19;
      long block18 = in.readLong();
      longs[idx++] = ((block17 & MASK32_6) << 13) | ((block18 >>> 19) & MASK32_13);
      longs[idx++] = block18 & MASK32_19;
    }
    expand32(longs);
  }

  private static void decode20(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 12) & MASK32_20;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_12) << 8) | ((block1 >>> 24) & MASK32_8);
      longs[idx++] = (block1 >>> 4) & MASK32_20;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_4) << 16) | ((block2 >>> 16) & MASK32_16);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_16) << 4) | ((block3 >>> 28) & MASK32_4);
      longs[idx++] = (block3 >>> 8) & MASK32_20;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_8) << 12) | ((block4 >>> 20) & MASK32_12);
      longs[idx++] = block4 & MASK32_20;
    }
    expand32(longs);
  }

  private static void decode21(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 11) & MASK32_21;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_11) << 10) | ((block1 >>> 22) & MASK32_10);
      longs[idx++] = (block1 >>> 1) & MASK32_21;
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_1) << 20) | ((block2 >>> 12) & MASK32_20);
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_12) << 9) | ((block3 >>> 23) & MASK32_9);
      longs[idx++] = (block3 >>> 2) & MASK32_21;
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_2) << 19) | ((block4 >>> 13) & MASK32_19);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_13) << 8) | ((block5 >>> 24) & MASK32_8);
      longs[idx++] = (block5 >>> 3) & MASK32_21;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_3) << 18) | ((block6 >>> 14) & MASK32_18);
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_14) << 7) | ((block7 >>> 25) & MASK32_7);
      longs[idx++] = (block7 >>> 4) & MASK32_21;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_4) << 17) | ((block8 >>> 15) & MASK32_17);
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK32_15) << 6) | ((block9 >>> 26) & MASK32_6);
      longs[idx++] = (block9 >>> 5) & MASK32_21;
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK32_5) << 16) | ((block10 >>> 16) & MASK32_16);
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK32_16) << 5) | ((block11 >>> 27) & MASK32_5);
      longs[idx++] = (block11 >>> 6) & MASK32_21;
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK32_6) << 15) | ((block12 >>> 17) & MASK32_15);
      long block13 = in.readLong();
      longs[idx++] = ((block12 & MASK32_17) << 4) | ((block13 >>> 28) & MASK32_4);
      longs[idx++] = (block13 >>> 7) & MASK32_21;
      long block14 = in.readLong();
      longs[idx++] = ((block13 & MASK32_7) << 14) | ((block14 >>> 18) & MASK32_14);
      long block15 = in.readLong();
      longs[idx++] = ((block14 & MASK32_18) << 3) | ((block15 >>> 29) & MASK32_3);
      longs[idx++] = (block15 >>> 8) & MASK32_21;
      long block16 = in.readLong();
      longs[idx++] = ((block15 & MASK32_8) << 13) | ((block16 >>> 19) & MASK32_13);
      long block17 = in.readLong();
      longs[idx++] = ((block16 & MASK32_19) << 2) | ((block17 >>> 30) & MASK32_2);
      longs[idx++] = (block17 >>> 9) & MASK32_21;
      long block18 = in.readLong();
      longs[idx++] = ((block17 & MASK32_9) << 12) | ((block18 >>> 20) & MASK32_12);
      long block19 = in.readLong();
      longs[idx++] = ((block18 & MASK32_20) << 1) | ((block19 >>> 31) & MASK32_1);
      longs[idx++] = (block19 >>> 10) & MASK32_21;
      long block20 = in.readLong();
      longs[idx++] = ((block19 & MASK32_10) << 11) | ((block20 >>> 21) & MASK32_11);
      longs[idx++] = block20 & MASK32_21;
    }
    expand32(longs);
  }

  private static void decode22(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 10) & MASK32_22;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_10) << 12) | ((block1 >>> 20) & MASK32_12);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_20) << 2) | ((block2 >>> 30) & MASK32_2);
      longs[idx++] = (block2 >>> 8) & MASK32_22;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_8) << 14) | ((block3 >>> 18) & MASK32_14);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_18) << 4) | ((block4 >>> 28) & MASK32_4);
      longs[idx++] = (block4 >>> 6) & MASK32_22;
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_6) << 16) | ((block5 >>> 16) & MASK32_16);
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_16) << 6) | ((block6 >>> 26) & MASK32_6);
      longs[idx++] = (block6 >>> 4) & MASK32_22;
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_4) << 18) | ((block7 >>> 14) & MASK32_18);
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_14) << 8) | ((block8 >>> 24) & MASK32_8);
      longs[idx++] = (block8 >>> 2) & MASK32_22;
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK32_2) << 20) | ((block9 >>> 12) & MASK32_20);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK32_12) << 10) | ((block10 >>> 22) & MASK32_10);
      longs[idx++] = block10 & MASK32_22;
    }
    expand32(longs);
  }

  private static void decode23(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 9) & MASK32_23;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_9) << 14) | ((block1 >>> 18) & MASK32_14);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_18) << 5) | ((block2 >>> 27) & MASK32_5);
      longs[idx++] = (block2 >>> 4) & MASK32_23;
      long block3 = in.readLong();
      longs[idx++] = ((block2 & MASK32_4) << 19) | ((block3 >>> 13) & MASK32_19);
      long block4 = in.readLong();
      longs[idx++] = ((block3 & MASK32_13) << 10) | ((block4 >>> 22) & MASK32_10);
      long block5 = in.readLong();
      longs[idx++] = ((block4 & MASK32_22) << 1) | ((block5 >>> 31) & MASK32_1);
      longs[idx++] = (block5 >>> 8) & MASK32_23;
      long block6 = in.readLong();
      longs[idx++] = ((block5 & MASK32_8) << 15) | ((block6 >>> 17) & MASK32_15);
      long block7 = in.readLong();
      longs[idx++] = ((block6 & MASK32_17) << 6) | ((block7 >>> 26) & MASK32_6);
      longs[idx++] = (block7 >>> 3) & MASK32_23;
      long block8 = in.readLong();
      longs[idx++] = ((block7 & MASK32_3) << 20) | ((block8 >>> 12) & MASK32_20);
      long block9 = in.readLong();
      longs[idx++] = ((block8 & MASK32_12) << 11) | ((block9 >>> 21) & MASK32_11);
      long block10 = in.readLong();
      longs[idx++] = ((block9 & MASK32_21) << 2) | ((block10 >>> 30) & MASK32_2);
      longs[idx++] = (block10 >>> 7) & MASK32_23;
      long block11 = in.readLong();
      longs[idx++] = ((block10 & MASK32_7) << 16) | ((block11 >>> 16) & MASK32_16);
      long block12 = in.readLong();
      longs[idx++] = ((block11 & MASK32_16) << 7) | ((block12 >>> 25) & MASK32_7);
      longs[idx++] = (block12 >>> 2) & MASK32_23;
      long block13 = in.readLong();
      longs[idx++] = ((block12 & MASK32_2) << 21) | ((block13 >>> 11) & MASK32_21);
      long block14 = in.readLong();
      longs[idx++] = ((block13 & MASK32_11) << 12) | ((block14 >>> 20) & MASK32_12);
      long block15 = in.readLong();
      longs[idx++] = ((block14 & MASK32_20) << 3) | ((block15 >>> 29) & MASK32_3);
      longs[idx++] = (block15 >>> 6) & MASK32_23;
      long block16 = in.readLong();
      longs[idx++] = ((block15 & MASK32_6) << 17) | ((block16 >>> 15) & MASK32_17);
      long block17 = in.readLong();
      longs[idx++] = ((block16 & MASK32_15) << 8) | ((block17 >>> 24) & MASK32_8);
      longs[idx++] = (block17 >>> 1) & MASK32_23;
      long block18 = in.readLong();
      longs[idx++] = ((block17 & MASK32_1) << 22) | ((block18 >>> 10) & MASK32_22);
      long block19 = in.readLong();
      longs[idx++] = ((block18 & MASK32_10) << 13) | ((block19 >>> 19) & MASK32_13);
      long block20 = in.readLong();
      longs[idx++] = ((block19 & MASK32_19) << 4) | ((block20 >>> 28) & MASK32_4);
      longs[idx++] = (block20 >>> 5) & MASK32_23;
      long block21 = in.readLong();
      longs[idx++] = ((block20 & MASK32_5) << 18) | ((block21 >>> 14) & MASK32_18);
      long block22 = in.readLong();
      longs[idx++] = ((block21 & MASK32_14) << 9) | ((block22 >>> 23) & MASK32_9);
      longs[idx++] = block22 & MASK32_23;
    }
    expand32(longs);
  }

  private static void decode24(DataInput in, long[] longs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 16; ++k) {
      long block0 = in.readLong();
      longs[idx++] = (block0 >>> 8) & MASK32_24;
      long block1 = in.readLong();
      longs[idx++] = ((block0 & MASK32_8) << 16) | ((block1 >>> 16) & MASK32_16);
      long block2 = in.readLong();
      longs[idx++] = ((block1 & MASK32_16) << 8) | ((block2 >>> 24) & MASK32_8);
      longs[idx++] = block2 & MASK32_24;
    }
    expand32(longs);
  }

}
