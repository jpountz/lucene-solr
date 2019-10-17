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
        nextBlock |= intPairs[idx++] << bitsLeft;
      } else if (bitsLeft == 0) {
        nextBlock |= intPairs[idx++];
        out.writeLong(nextBlock);
        nextBlock = 0;
        bitsLeft = 32;
      } else {
        final long intPair = intPairs[idx++];
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

  private static final long MASK_1 = mask(1);
  private static final long MASK_2 = mask(2);
  private static final long MASK_3 = mask(3);
  private static final long MASK_4 = mask(4);
  private static final long MASK_5 = mask(5);
  private static final long MASK_6 = mask(6);
  private static final long MASK_7 = mask(7);
  private static final long MASK_8 = mask(8);
  private static final long MASK_9 = mask(9);
  private static final long MASK_10 = mask(10);
  private static final long MASK_11 = mask(11);
  private static final long MASK_12 = mask(12);
  private static final long MASK_13 = mask(13);
  private static final long MASK_14 = mask(14);
  private static final long MASK_15 = mask(15);
  private static final long MASK_16 = mask(16);
  private static final long MASK_17 = mask(17);
  private static final long MASK_18 = mask(18);
  private static final long MASK_19 = mask(19);
  private static final long MASK_20 = mask(20);
  private static final long MASK_21 = mask(21);
  private static final long MASK_22 = mask(22);
  private static final long MASK_23 = mask(23);
  private static final long MASK_24 = mask(24);


  /**
   * Decode 128 integers into {@code ints}.
   */
  void decode(DataInput in, IntArray ints) throws IOException {
    final int bitsPerValue = in.readByte();
    switch (bitsPerValue) {
    case 1:
      decode1(in, ints.longs);
      break;
    case 2:
      decode2(in, ints.longs);
      break;
    case 3:
      decode3(in, ints.longs);
      break;
    case 4:
      decode4(in, ints.longs);
      break;
    case 5:
      decode5(in, ints.longs);
      break;
    case 6:
      decode6(in, ints.longs);
      break;
    case 7:
      decode7(in, ints.longs);
      break;
    case 8:
      decode8(in, ints.longs);
      break;
    case 9:
      decode9(in, ints.longs);
      break;
    case 10:
      decode10(in, ints.longs);
      break;
    case 11:
      decode11(in, ints.longs);
      break;
    case 12:
      decode12(in, ints.longs);
      break;
    case 13:
      decode13(in, ints.longs);
      break;
    case 14:
      decode14(in, ints.longs);
      break;
    case 15:
      decode15(in, ints.longs);
      break;
    case 16:
      decode16(in, ints.longs);
      break;
    case 17:
      decode17(in, ints.longs);
      break;
    case 18:
      decode18(in, ints.longs);
      break;
    case 19:
      decode19(in, ints.longs);
      break;
    case 20:
      decode20(in, ints.longs);
      break;
    case 21:
      decode21(in, ints.longs);
      break;
    case 22:
      decode22(in, ints.longs);
      break;
    case 23:
      decode23(in, ints.longs);
      break;
    case 24:
      decode24(in, ints.longs);
      break;
    default:
      decodeSlow(bitsPerValue, in, ints.longs);
      break;
    }
  }

  private static void decode1(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 31; shift >= 0; shift -= 1) {
        intPairs[idx++] = (block0 >>> shift) & MASK_1;
      }
    }
  }

  private static void decode2(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 30; shift >= 0; shift -= 2) {
        intPairs[idx++] = (block0 >>> shift) & MASK_2;
      }
    }
  }

  private static void decode3(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 29; shift >= 0; shift -= 3) {
        intPairs[idx++] = (block0 >>> shift) & MASK_3;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_2) << 1) | ((block1 >>> 31) & MASK_1);
      for (int shift = 28; shift >= 0; shift -= 3) {
        intPairs[idx++] = (block1 >>> shift) & MASK_3;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_1) << 2) | ((block2 >>> 30) & MASK_2);
      for (int shift = 27; shift >= 0; shift -= 3) {
        intPairs[idx++] = (block2 >>> shift) & MASK_3;
      }
    }
  }

  private static void decode4(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      for (int shift = 28; shift >= 0; shift -= 4) {
        intPairs[idx++] = (block0 >>> shift) & MASK_4;
      }
    }
  }

  private static void decode5(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 27; shift >= 0; shift -= 5) {
        intPairs[idx++] = (block0 >>> shift) & MASK_5;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_2) << 3) | ((block1 >>> 29) & MASK_3);
      for (int shift = 24; shift >= 0; shift -= 5) {
        intPairs[idx++] = (block1 >>> shift) & MASK_5;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 1) | ((block2 >>> 31) & MASK_1);
      for (int shift = 26; shift >= 0; shift -= 5) {
        intPairs[idx++] = (block2 >>> shift) & MASK_5;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_1) << 4) | ((block3 >>> 28) & MASK_4);
      for (int shift = 23; shift >= 0; shift -= 5) {
        intPairs[idx++] = (block3 >>> shift) & MASK_5;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_3) << 2) | ((block4 >>> 30) & MASK_2);
      for (int shift = 25; shift >= 0; shift -= 5) {
        intPairs[idx++] = (block4 >>> shift) & MASK_5;
      }
    }
  }

  private static void decode6(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 26; shift >= 0; shift -= 6) {
        intPairs[idx++] = (block0 >>> shift) & MASK_6;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_2) << 4) | ((block1 >>> 28) & MASK_4);
      for (int shift = 22; shift >= 0; shift -= 6) {
        intPairs[idx++] = (block1 >>> shift) & MASK_6;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 2) | ((block2 >>> 30) & MASK_2);
      for (int shift = 24; shift >= 0; shift -= 6) {
        intPairs[idx++] = (block2 >>> shift) & MASK_6;
      }
    }
  }

  private static void decode7(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 25; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block0 >>> shift) & MASK_7;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_4) << 3) | ((block1 >>> 29) & MASK_3);
      for (int shift = 22; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block1 >>> shift) & MASK_7;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_1) << 6) | ((block2 >>> 26) & MASK_6);
      for (int shift = 19; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block2 >>> shift) & MASK_7;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_5) << 2) | ((block3 >>> 30) & MASK_2);
      for (int shift = 23; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block3 >>> shift) & MASK_7;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_2) << 5) | ((block4 >>> 27) & MASK_5);
      for (int shift = 20; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block4 >>> shift) & MASK_7;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_6) << 1) | ((block5 >>> 31) & MASK_1);
      for (int shift = 24; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block5 >>> shift) & MASK_7;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_3) << 4) | ((block6 >>> 28) & MASK_4);
      for (int shift = 21; shift >= 0; shift -= 7) {
        intPairs[idx++] = (block6 >>> shift) & MASK_7;
      }
    }
  }

  private static void decode8(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 16; ++k) {
      long block0 = in.readLong();
      for (int shift = 24; shift >= 0; shift -= 8) {
        intPairs[idx++] = (block0 >>> shift) & MASK_8;
      }
    }
  }

  private static void decode9(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 23; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block0 >>> shift) & MASK_9;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_5) << 4) | ((block1 >>> 28) & MASK_4);
      for (int shift = 19; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block1 >>> shift) & MASK_9;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_1) << 8) | ((block2 >>> 24) & MASK_8);
      for (int shift = 15; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block2 >>> shift) & MASK_9;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_6) << 3) | ((block3 >>> 29) & MASK_3);
      for (int shift = 20; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block3 >>> shift) & MASK_9;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_2) << 7) | ((block4 >>> 25) & MASK_7);
      for (int shift = 16; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block4 >>> shift) & MASK_9;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_7) << 2) | ((block5 >>> 30) & MASK_2);
      for (int shift = 21; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block5 >>> shift) & MASK_9;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_3) << 6) | ((block6 >>> 26) & MASK_6);
      for (int shift = 17; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block6 >>> shift) & MASK_9;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_8) << 1) | ((block7 >>> 31) & MASK_1);
      for (int shift = 22; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block7 >>> shift) & MASK_9;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_4) << 5) | ((block8 >>> 27) & MASK_5);
      for (int shift = 18; shift >= 0; shift -= 9) {
        intPairs[idx++] = (block8 >>> shift) & MASK_9;
      }
    }
  }

  private static void decode10(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 22; shift >= 0; shift -= 10) {
        intPairs[idx++] = (block0 >>> shift) & MASK_10;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_2) << 8) | ((block1 >>> 24) & MASK_8);
      for (int shift = 14; shift >= 0; shift -= 10) {
        intPairs[idx++] = (block1 >>> shift) & MASK_10;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 6) | ((block2 >>> 26) & MASK_6);
      for (int shift = 16; shift >= 0; shift -= 10) {
        intPairs[idx++] = (block2 >>> shift) & MASK_10;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_6) << 4) | ((block3 >>> 28) & MASK_4);
      for (int shift = 18; shift >= 0; shift -= 10) {
        intPairs[idx++] = (block3 >>> shift) & MASK_10;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_8) << 2) | ((block4 >>> 30) & MASK_2);
      for (int shift = 20; shift >= 0; shift -= 10) {
        intPairs[idx++] = (block4 >>> shift) & MASK_10;
      }
    }
  }

  private static void decode11(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 21; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block0 >>> shift) & MASK_11;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_10) << 1) | ((block1 >>> 31) & MASK_1);
      for (int shift = 20; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block1 >>> shift) & MASK_11;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_9) << 2) | ((block2 >>> 30) & MASK_2);
      for (int shift = 19; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block2 >>> shift) & MASK_11;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_8) << 3) | ((block3 >>> 29) & MASK_3);
      for (int shift = 18; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block3 >>> shift) & MASK_11;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_7) << 4) | ((block4 >>> 28) & MASK_4);
      for (int shift = 17; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block4 >>> shift) & MASK_11;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_6) << 5) | ((block5 >>> 27) & MASK_5);
      for (int shift = 16; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block5 >>> shift) & MASK_11;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_5) << 6) | ((block6 >>> 26) & MASK_6);
      for (int shift = 15; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block6 >>> shift) & MASK_11;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_4) << 7) | ((block7 >>> 25) & MASK_7);
      for (int shift = 14; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block7 >>> shift) & MASK_11;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_3) << 8) | ((block8 >>> 24) & MASK_8);
      for (int shift = 13; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block8 >>> shift) & MASK_11;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_2) << 9) | ((block9 >>> 23) & MASK_9);
      for (int shift = 12; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block9 >>> shift) & MASK_11;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_1) << 10) | ((block10 >>> 22) & MASK_10);
      for (int shift = 11; shift >= 0; shift -= 11) {
        intPairs[idx++] = (block10 >>> shift) & MASK_11;
      }
    }
  }

  private static void decode12(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      for (int shift = 20; shift >= 0; shift -= 12) {
        intPairs[idx++] = (block0 >>> shift) & MASK_12;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_8) << 4) | ((block1 >>> 28) & MASK_4);
      for (int shift = 16; shift >= 0; shift -= 12) {
        intPairs[idx++] = (block1 >>> shift) & MASK_12;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 8) | ((block2 >>> 24) & MASK_8);
      for (int shift = 12; shift >= 0; shift -= 12) {
        intPairs[idx++] = (block2 >>> shift) & MASK_12;
      }
    }
  }

  private static void decode13(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 19; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block0 >>> shift) & MASK_13;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_6) << 7) | ((block1 >>> 25) & MASK_7);
      for (int shift = 12; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block1 >>> shift) & MASK_13;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_12) << 1) | ((block2 >>> 31) & MASK_1);
      for (int shift = 18; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block2 >>> shift) & MASK_13;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_5) << 8) | ((block3 >>> 24) & MASK_8);
      for (int shift = 11; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block3 >>> shift) & MASK_13;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_11) << 2) | ((block4 >>> 30) & MASK_2);
      for (int shift = 17; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block4 >>> shift) & MASK_13;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_4) << 9) | ((block5 >>> 23) & MASK_9);
      for (int shift = 10; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block5 >>> shift) & MASK_13;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_10) << 3) | ((block6 >>> 29) & MASK_3);
      for (int shift = 16; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block6 >>> shift) & MASK_13;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_3) << 10) | ((block7 >>> 22) & MASK_10);
      for (int shift = 9; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block7 >>> shift) & MASK_13;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_9) << 4) | ((block8 >>> 28) & MASK_4);
      for (int shift = 15; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block8 >>> shift) & MASK_13;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_2) << 11) | ((block9 >>> 21) & MASK_11);
      for (int shift = 8; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block9 >>> shift) & MASK_13;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_8) << 5) | ((block10 >>> 27) & MASK_5);
      for (int shift = 14; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block10 >>> shift) & MASK_13;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_1) << 12) | ((block11 >>> 20) & MASK_12);
      for (int shift = 7; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block11 >>> shift) & MASK_13;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_7) << 6) | ((block12 >>> 26) & MASK_6);
      for (int shift = 13; shift >= 0; shift -= 13) {
        intPairs[idx++] = (block12 >>> shift) & MASK_13;
      }
    }
  }

  private static void decode14(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 18; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block0 >>> shift) & MASK_14;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_4) << 10) | ((block1 >>> 22) & MASK_10);
      for (int shift = 8; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block1 >>> shift) & MASK_14;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_8) << 6) | ((block2 >>> 26) & MASK_6);
      for (int shift = 12; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block2 >>> shift) & MASK_14;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_12) << 2) | ((block3 >>> 30) & MASK_2);
      for (int shift = 16; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block3 >>> shift) & MASK_14;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_2) << 12) | ((block4 >>> 20) & MASK_12);
      for (int shift = 6; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block4 >>> shift) & MASK_14;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_6) << 8) | ((block5 >>> 24) & MASK_8);
      for (int shift = 10; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block5 >>> shift) & MASK_14;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_10) << 4) | ((block6 >>> 28) & MASK_4);
      for (int shift = 14; shift >= 0; shift -= 14) {
        intPairs[idx++] = (block6 >>> shift) & MASK_14;
      }
    }
  }

  private static void decode15(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 17; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block0 >>> shift) & MASK_15;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_2) << 13) | ((block1 >>> 19) & MASK_13);
      for (int shift = 4; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block1 >>> shift) & MASK_15;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 11) | ((block2 >>> 21) & MASK_11);
      for (int shift = 6; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block2 >>> shift) & MASK_15;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_6) << 9) | ((block3 >>> 23) & MASK_9);
      for (int shift = 8; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block3 >>> shift) & MASK_15;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_8) << 7) | ((block4 >>> 25) & MASK_7);
      for (int shift = 10; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block4 >>> shift) & MASK_15;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_10) << 5) | ((block5 >>> 27) & MASK_5);
      for (int shift = 12; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block5 >>> shift) & MASK_15;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_12) << 3) | ((block6 >>> 29) & MASK_3);
      for (int shift = 14; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block6 >>> shift) & MASK_15;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_14) << 1) | ((block7 >>> 31) & MASK_1);
      for (int shift = 16; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block7 >>> shift) & MASK_15;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_1) << 14) | ((block8 >>> 18) & MASK_14);
      for (int shift = 3; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block8 >>> shift) & MASK_15;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_3) << 12) | ((block9 >>> 20) & MASK_12);
      for (int shift = 5; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block9 >>> shift) & MASK_15;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_5) << 10) | ((block10 >>> 22) & MASK_10);
      for (int shift = 7; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block10 >>> shift) & MASK_15;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_7) << 8) | ((block11 >>> 24) & MASK_8);
      for (int shift = 9; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block11 >>> shift) & MASK_15;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_9) << 6) | ((block12 >>> 26) & MASK_6);
      for (int shift = 11; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block12 >>> shift) & MASK_15;
      }
      long block13 = in.readLong();
      intPairs[idx++] = ((block12 & MASK_11) << 4) | ((block13 >>> 28) & MASK_4);
      for (int shift = 13; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block13 >>> shift) & MASK_15;
      }
      long block14 = in.readLong();
      intPairs[idx++] = ((block13 & MASK_13) << 2) | ((block14 >>> 30) & MASK_2);
      for (int shift = 15; shift >= 0; shift -= 15) {
        intPairs[idx++] = (block14 >>> shift) & MASK_15;
      }
    }
  }

  private static void decode16(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 32; ++k) {
      long block0 = in.readLong();
      for (int shift = 16; shift >= 0; shift -= 16) {
        intPairs[idx++] = (block0 >>> shift) & MASK_16;
      }
    }
  }

  private static void decode17(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 15; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block0 >>> shift) & MASK_17;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_15) << 2) | ((block1 >>> 30) & MASK_2);
      for (int shift = 13; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block1 >>> shift) & MASK_17;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_13) << 4) | ((block2 >>> 28) & MASK_4);
      for (int shift = 11; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block2 >>> shift) & MASK_17;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_11) << 6) | ((block3 >>> 26) & MASK_6);
      for (int shift = 9; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block3 >>> shift) & MASK_17;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_9) << 8) | ((block4 >>> 24) & MASK_8);
      for (int shift = 7; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block4 >>> shift) & MASK_17;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_7) << 10) | ((block5 >>> 22) & MASK_10);
      for (int shift = 5; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block5 >>> shift) & MASK_17;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_5) << 12) | ((block6 >>> 20) & MASK_12);
      for (int shift = 3; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block6 >>> shift) & MASK_17;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_3) << 14) | ((block7 >>> 18) & MASK_14);
      for (int shift = 1; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block7 >>> shift) & MASK_17;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_1) << 16) | ((block8 >>> 16) & MASK_16);
      for (int shift = -1; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block8 >>> shift) & MASK_17;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_16) << 1) | ((block9 >>> 31) & MASK_1);
      for (int shift = 14; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block9 >>> shift) & MASK_17;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_14) << 3) | ((block10 >>> 29) & MASK_3);
      for (int shift = 12; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block10 >>> shift) & MASK_17;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_12) << 5) | ((block11 >>> 27) & MASK_5);
      for (int shift = 10; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block11 >>> shift) & MASK_17;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_10) << 7) | ((block12 >>> 25) & MASK_7);
      for (int shift = 8; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block12 >>> shift) & MASK_17;
      }
      long block13 = in.readLong();
      intPairs[idx++] = ((block12 & MASK_8) << 9) | ((block13 >>> 23) & MASK_9);
      for (int shift = 6; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block13 >>> shift) & MASK_17;
      }
      long block14 = in.readLong();
      intPairs[idx++] = ((block13 & MASK_6) << 11) | ((block14 >>> 21) & MASK_11);
      for (int shift = 4; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block14 >>> shift) & MASK_17;
      }
      long block15 = in.readLong();
      intPairs[idx++] = ((block14 & MASK_4) << 13) | ((block15 >>> 19) & MASK_13);
      for (int shift = 2; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block15 >>> shift) & MASK_17;
      }
      long block16 = in.readLong();
      intPairs[idx++] = ((block15 & MASK_2) << 15) | ((block16 >>> 17) & MASK_15);
      for (int shift = 0; shift >= 0; shift -= 17) {
        intPairs[idx++] = (block16 >>> shift) & MASK_17;
      }
    }
  }

  private static void decode18(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 14; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block0 >>> shift) & MASK_18;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_14) << 4) | ((block1 >>> 28) & MASK_4);
      for (int shift = 10; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block1 >>> shift) & MASK_18;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_10) << 8) | ((block2 >>> 24) & MASK_8);
      for (int shift = 6; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block2 >>> shift) & MASK_18;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_6) << 12) | ((block3 >>> 20) & MASK_12);
      for (int shift = 2; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block3 >>> shift) & MASK_18;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_2) << 16) | ((block4 >>> 16) & MASK_16);
      for (int shift = -2; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block4 >>> shift) & MASK_18;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_16) << 2) | ((block5 >>> 30) & MASK_2);
      for (int shift = 12; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block5 >>> shift) & MASK_18;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_12) << 6) | ((block6 >>> 26) & MASK_6);
      for (int shift = 8; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block6 >>> shift) & MASK_18;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_8) << 10) | ((block7 >>> 22) & MASK_10);
      for (int shift = 4; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block7 >>> shift) & MASK_18;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_4) << 14) | ((block8 >>> 18) & MASK_14);
      for (int shift = 0; shift >= 0; shift -= 18) {
        intPairs[idx++] = (block8 >>> shift) & MASK_18;
      }
    }
  }

  private static void decode19(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 13; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block0 >>> shift) & MASK_19;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_13) << 6) | ((block1 >>> 26) & MASK_6);
      for (int shift = 7; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block1 >>> shift) & MASK_19;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_7) << 12) | ((block2 >>> 20) & MASK_12);
      for (int shift = 1; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block2 >>> shift) & MASK_19;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_1) << 18) | ((block3 >>> 14) & MASK_18);
      for (int shift = -5; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block3 >>> shift) & MASK_19;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_14) << 5) | ((block4 >>> 27) & MASK_5);
      for (int shift = 8; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block4 >>> shift) & MASK_19;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_8) << 11) | ((block5 >>> 21) & MASK_11);
      for (int shift = 2; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block5 >>> shift) & MASK_19;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_2) << 17) | ((block6 >>> 15) & MASK_17);
      for (int shift = -4; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block6 >>> shift) & MASK_19;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_15) << 4) | ((block7 >>> 28) & MASK_4);
      for (int shift = 9; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block7 >>> shift) & MASK_19;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_9) << 10) | ((block8 >>> 22) & MASK_10);
      for (int shift = 3; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block8 >>> shift) & MASK_19;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_3) << 16) | ((block9 >>> 16) & MASK_16);
      for (int shift = -3; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block9 >>> shift) & MASK_19;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_16) << 3) | ((block10 >>> 29) & MASK_3);
      for (int shift = 10; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block10 >>> shift) & MASK_19;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_10) << 9) | ((block11 >>> 23) & MASK_9);
      for (int shift = 4; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block11 >>> shift) & MASK_19;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_4) << 15) | ((block12 >>> 17) & MASK_15);
      for (int shift = -2; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block12 >>> shift) & MASK_19;
      }
      long block13 = in.readLong();
      intPairs[idx++] = ((block12 & MASK_17) << 2) | ((block13 >>> 30) & MASK_2);
      for (int shift = 11; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block13 >>> shift) & MASK_19;
      }
      long block14 = in.readLong();
      intPairs[idx++] = ((block13 & MASK_11) << 8) | ((block14 >>> 24) & MASK_8);
      for (int shift = 5; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block14 >>> shift) & MASK_19;
      }
      long block15 = in.readLong();
      intPairs[idx++] = ((block14 & MASK_5) << 14) | ((block15 >>> 18) & MASK_14);
      for (int shift = -1; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block15 >>> shift) & MASK_19;
      }
      long block16 = in.readLong();
      intPairs[idx++] = ((block15 & MASK_18) << 1) | ((block16 >>> 31) & MASK_1);
      for (int shift = 12; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block16 >>> shift) & MASK_19;
      }
      long block17 = in.readLong();
      intPairs[idx++] = ((block16 & MASK_12) << 7) | ((block17 >>> 25) & MASK_7);
      for (int shift = 6; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block17 >>> shift) & MASK_19;
      }
      long block18 = in.readLong();
      intPairs[idx++] = ((block17 & MASK_6) << 13) | ((block18 >>> 19) & MASK_13);
      for (int shift = 0; shift >= 0; shift -= 19) {
        intPairs[idx++] = (block18 >>> shift) & MASK_19;
      }
    }
  }

  private static void decode20(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 8; ++k) {
      long block0 = in.readLong();
      for (int shift = 12; shift >= 0; shift -= 20) {
        intPairs[idx++] = (block0 >>> shift) & MASK_20;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_12) << 8) | ((block1 >>> 24) & MASK_8);
      for (int shift = 4; shift >= 0; shift -= 20) {
        intPairs[idx++] = (block1 >>> shift) & MASK_20;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_4) << 16) | ((block2 >>> 16) & MASK_16);
      for (int shift = -4; shift >= 0; shift -= 20) {
        intPairs[idx++] = (block2 >>> shift) & MASK_20;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_16) << 4) | ((block3 >>> 28) & MASK_4);
      for (int shift = 8; shift >= 0; shift -= 20) {
        intPairs[idx++] = (block3 >>> shift) & MASK_20;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_8) << 12) | ((block4 >>> 20) & MASK_12);
      for (int shift = 0; shift >= 0; shift -= 20) {
        intPairs[idx++] = (block4 >>> shift) & MASK_20;
      }
    }
  }

  private static void decode21(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 11; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block0 >>> shift) & MASK_21;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_11) << 10) | ((block1 >>> 22) & MASK_10);
      for (int shift = 1; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block1 >>> shift) & MASK_21;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_1) << 20) | ((block2 >>> 12) & MASK_20);
      for (int shift = -9; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block2 >>> shift) & MASK_21;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_12) << 9) | ((block3 >>> 23) & MASK_9);
      for (int shift = 2; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block3 >>> shift) & MASK_21;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_2) << 19) | ((block4 >>> 13) & MASK_19);
      for (int shift = -8; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block4 >>> shift) & MASK_21;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_13) << 8) | ((block5 >>> 24) & MASK_8);
      for (int shift = 3; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block5 >>> shift) & MASK_21;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_3) << 18) | ((block6 >>> 14) & MASK_18);
      for (int shift = -7; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block6 >>> shift) & MASK_21;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_14) << 7) | ((block7 >>> 25) & MASK_7);
      for (int shift = 4; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block7 >>> shift) & MASK_21;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_4) << 17) | ((block8 >>> 15) & MASK_17);
      for (int shift = -6; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block8 >>> shift) & MASK_21;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_15) << 6) | ((block9 >>> 26) & MASK_6);
      for (int shift = 5; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block9 >>> shift) & MASK_21;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_5) << 16) | ((block10 >>> 16) & MASK_16);
      for (int shift = -5; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block10 >>> shift) & MASK_21;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_16) << 5) | ((block11 >>> 27) & MASK_5);
      for (int shift = 6; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block11 >>> shift) & MASK_21;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_6) << 15) | ((block12 >>> 17) & MASK_15);
      for (int shift = -4; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block12 >>> shift) & MASK_21;
      }
      long block13 = in.readLong();
      intPairs[idx++] = ((block12 & MASK_17) << 4) | ((block13 >>> 28) & MASK_4);
      for (int shift = 7; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block13 >>> shift) & MASK_21;
      }
      long block14 = in.readLong();
      intPairs[idx++] = ((block13 & MASK_7) << 14) | ((block14 >>> 18) & MASK_14);
      for (int shift = -3; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block14 >>> shift) & MASK_21;
      }
      long block15 = in.readLong();
      intPairs[idx++] = ((block14 & MASK_18) << 3) | ((block15 >>> 29) & MASK_3);
      for (int shift = 8; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block15 >>> shift) & MASK_21;
      }
      long block16 = in.readLong();
      intPairs[idx++] = ((block15 & MASK_8) << 13) | ((block16 >>> 19) & MASK_13);
      for (int shift = -2; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block16 >>> shift) & MASK_21;
      }
      long block17 = in.readLong();
      intPairs[idx++] = ((block16 & MASK_19) << 2) | ((block17 >>> 30) & MASK_2);
      for (int shift = 9; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block17 >>> shift) & MASK_21;
      }
      long block18 = in.readLong();
      intPairs[idx++] = ((block17 & MASK_9) << 12) | ((block18 >>> 20) & MASK_12);
      for (int shift = -1; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block18 >>> shift) & MASK_21;
      }
      long block19 = in.readLong();
      intPairs[idx++] = ((block18 & MASK_20) << 1) | ((block19 >>> 31) & MASK_1);
      for (int shift = 10; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block19 >>> shift) & MASK_21;
      }
      long block20 = in.readLong();
      intPairs[idx++] = ((block19 & MASK_10) << 11) | ((block20 >>> 21) & MASK_11);
      for (int shift = 0; shift >= 0; shift -= 21) {
        intPairs[idx++] = (block20 >>> shift) & MASK_21;
      }
    }
  }

  private static void decode22(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 4; ++k) {
      long block0 = in.readLong();
      for (int shift = 10; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block0 >>> shift) & MASK_22;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_10) << 12) | ((block1 >>> 20) & MASK_12);
      for (int shift = -2; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block1 >>> shift) & MASK_22;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_20) << 2) | ((block2 >>> 30) & MASK_2);
      for (int shift = 8; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block2 >>> shift) & MASK_22;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_8) << 14) | ((block3 >>> 18) & MASK_14);
      for (int shift = -4; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block3 >>> shift) & MASK_22;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_18) << 4) | ((block4 >>> 28) & MASK_4);
      for (int shift = 6; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block4 >>> shift) & MASK_22;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_6) << 16) | ((block5 >>> 16) & MASK_16);
      for (int shift = -6; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block5 >>> shift) & MASK_22;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_16) << 6) | ((block6 >>> 26) & MASK_6);
      for (int shift = 4; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block6 >>> shift) & MASK_22;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_4) << 18) | ((block7 >>> 14) & MASK_18);
      for (int shift = -8; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block7 >>> shift) & MASK_22;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_14) << 8) | ((block8 >>> 24) & MASK_8);
      for (int shift = 2; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block8 >>> shift) & MASK_22;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_2) << 20) | ((block9 >>> 12) & MASK_20);
      for (int shift = -10; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block9 >>> shift) & MASK_22;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_12) << 10) | ((block10 >>> 22) & MASK_10);
      for (int shift = 0; shift >= 0; shift -= 22) {
        intPairs[idx++] = (block10 >>> shift) & MASK_22;
      }
    }
  }

  private static void decode23(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 2; ++k) {
      long block0 = in.readLong();
      for (int shift = 9; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block0 >>> shift) & MASK_23;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_9) << 14) | ((block1 >>> 18) & MASK_14);
      for (int shift = -5; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block1 >>> shift) & MASK_23;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_18) << 5) | ((block2 >>> 27) & MASK_5);
      for (int shift = 4; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block2 >>> shift) & MASK_23;
      }
      long block3 = in.readLong();
      intPairs[idx++] = ((block2 & MASK_4) << 19) | ((block3 >>> 13) & MASK_19);
      for (int shift = -10; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block3 >>> shift) & MASK_23;
      }
      long block4 = in.readLong();
      intPairs[idx++] = ((block3 & MASK_13) << 10) | ((block4 >>> 22) & MASK_10);
      for (int shift = -1; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block4 >>> shift) & MASK_23;
      }
      long block5 = in.readLong();
      intPairs[idx++] = ((block4 & MASK_22) << 1) | ((block5 >>> 31) & MASK_1);
      for (int shift = 8; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block5 >>> shift) & MASK_23;
      }
      long block6 = in.readLong();
      intPairs[idx++] = ((block5 & MASK_8) << 15) | ((block6 >>> 17) & MASK_15);
      for (int shift = -6; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block6 >>> shift) & MASK_23;
      }
      long block7 = in.readLong();
      intPairs[idx++] = ((block6 & MASK_17) << 6) | ((block7 >>> 26) & MASK_6);
      for (int shift = 3; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block7 >>> shift) & MASK_23;
      }
      long block8 = in.readLong();
      intPairs[idx++] = ((block7 & MASK_3) << 20) | ((block8 >>> 12) & MASK_20);
      for (int shift = -11; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block8 >>> shift) & MASK_23;
      }
      long block9 = in.readLong();
      intPairs[idx++] = ((block8 & MASK_12) << 11) | ((block9 >>> 21) & MASK_11);
      for (int shift = -2; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block9 >>> shift) & MASK_23;
      }
      long block10 = in.readLong();
      intPairs[idx++] = ((block9 & MASK_21) << 2) | ((block10 >>> 30) & MASK_2);
      for (int shift = 7; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block10 >>> shift) & MASK_23;
      }
      long block11 = in.readLong();
      intPairs[idx++] = ((block10 & MASK_7) << 16) | ((block11 >>> 16) & MASK_16);
      for (int shift = -7; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block11 >>> shift) & MASK_23;
      }
      long block12 = in.readLong();
      intPairs[idx++] = ((block11 & MASK_16) << 7) | ((block12 >>> 25) & MASK_7);
      for (int shift = 2; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block12 >>> shift) & MASK_23;
      }
      long block13 = in.readLong();
      intPairs[idx++] = ((block12 & MASK_2) << 21) | ((block13 >>> 11) & MASK_21);
      for (int shift = -12; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block13 >>> shift) & MASK_23;
      }
      long block14 = in.readLong();
      intPairs[idx++] = ((block13 & MASK_11) << 12) | ((block14 >>> 20) & MASK_12);
      for (int shift = -3; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block14 >>> shift) & MASK_23;
      }
      long block15 = in.readLong();
      intPairs[idx++] = ((block14 & MASK_20) << 3) | ((block15 >>> 29) & MASK_3);
      for (int shift = 6; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block15 >>> shift) & MASK_23;
      }
      long block16 = in.readLong();
      intPairs[idx++] = ((block15 & MASK_6) << 17) | ((block16 >>> 15) & MASK_17);
      for (int shift = -8; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block16 >>> shift) & MASK_23;
      }
      long block17 = in.readLong();
      intPairs[idx++] = ((block16 & MASK_15) << 8) | ((block17 >>> 24) & MASK_8);
      for (int shift = 1; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block17 >>> shift) & MASK_23;
      }
      long block18 = in.readLong();
      intPairs[idx++] = ((block17 & MASK_1) << 22) | ((block18 >>> 10) & MASK_22);
      for (int shift = -13; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block18 >>> shift) & MASK_23;
      }
      long block19 = in.readLong();
      intPairs[idx++] = ((block18 & MASK_10) << 13) | ((block19 >>> 19) & MASK_13);
      for (int shift = -4; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block19 >>> shift) & MASK_23;
      }
      long block20 = in.readLong();
      intPairs[idx++] = ((block19 & MASK_19) << 4) | ((block20 >>> 28) & MASK_4);
      for (int shift = 5; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block20 >>> shift) & MASK_23;
      }
      long block21 = in.readLong();
      intPairs[idx++] = ((block20 & MASK_5) << 18) | ((block21 >>> 14) & MASK_18);
      for (int shift = -9; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block21 >>> shift) & MASK_23;
      }
      long block22 = in.readLong();
      intPairs[idx++] = ((block21 & MASK_14) << 9) | ((block22 >>> 23) & MASK_9);
      for (int shift = 0; shift >= 0; shift -= 23) {
        intPairs[idx++] = (block22 >>> shift) & MASK_23;
      }
    }
  }

  private static void decode24(DataInput in, long[] intPairs) throws IOException {
    int idx = 0;
    for (int k = 0; k < 16; ++k) {
      long block0 = in.readLong();
      for (int shift = 8; shift >= 0; shift -= 24) {
        intPairs[idx++] = (block0 >>> shift) & MASK_24;
      }
      long block1 = in.readLong();
      intPairs[idx++] = ((block0 & MASK_8) << 16) | ((block1 >>> 16) & MASK_16);
      for (int shift = -8; shift >= 0; shift -= 24) {
        intPairs[idx++] = (block1 >>> shift) & MASK_24;
      }
      long block2 = in.readLong();
      intPairs[idx++] = ((block1 & MASK_16) << 8) | ((block2 >>> 24) & MASK_8);
      for (int shift = 0; shift >= 0; shift -= 24) {
        intPairs[idx++] = (block2 >>> shift) & MASK_24;
      }
    }
  }

}
