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

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.packed.PackedInts;
import org.carrot2.mahout.math.Arrays;

import com.carrotsearch.randomizedtesting.generators.RandomNumbers;

public class TestForUtil extends LuceneTestCase {

  public void testEncodeDecode() throws IOException {
    final int iterations = RandomNumbers.randomIntBetween(random(), 50, 1000);
    final int[] values = new int[iterations * ForUtil.BLOCK_SIZE];

    for (int i = 0; i < iterations; ++i) {
      final int bpv = random().nextInt(32);
      if (bpv == 0) {
        final int value = RandomNumbers.randomIntBetween(random(), 0, Integer.MAX_VALUE);
        for (int j = 0; j < ForUtil.BLOCK_SIZE; ++j) {
          values[i * ForUtil.BLOCK_SIZE + j] = value;
        }
      } else {
        for (int j = 0; j < ForUtil.BLOCK_SIZE; ++j) {
          values[i * ForUtil.BLOCK_SIZE + j] = RandomNumbers.randomIntBetween(random(),
              0, (int) PackedInts.maxValue(bpv));
        }
      }
    }

    final Directory d = new ByteBuffersDirectory();
    final long endPointer;

    {
      // encode
      IndexOutput out = d.createOutput("test.bin", IOContext.DEFAULT);
      final ForUtil forUtil = new ForUtil();

      for (int i = 0; i < iterations; ++i) {
        long[] source = new long[ForUtil.BLOCK_SIZE];
        for (int j = 0; j < ForUtil.BLOCK_SIZE; ++j) {
          source[j] = values[i*ForUtil.BLOCK_SIZE+j];
        }
        forUtil.encode(source, out);
      }
      endPointer = out.getFilePointer();
      out.close();
    }

    {
      // decode
      IndexInput in = d.openInput("test.bin", IOContext.READONCE);
      final ForUtil forUtil = new ForUtil();
      for (int i = 0; i < iterations; ++i) {
        if (random().nextBoolean()) {
          forUtil.skip(in);
          continue;
        }
        final long[] restored = new long[ForUtil.BLOCK_SIZE];
        forUtil.decode(in, restored);
        int[] ints = new int[ForUtil.BLOCK_SIZE];
        for (int j = 0; j < ForUtil.BLOCK_SIZE; ++j) {
          ints[j] = Math.toIntExact(restored[j]);
        }
        assertArrayEquals(Arrays.toString(ints),
            ArrayUtil.copyOfSubArray(values, i*ForUtil.BLOCK_SIZE, (i+1)*ForUtil.BLOCK_SIZE),
            ints);
      }
      assertEquals(endPointer, in.getFilePointer());
      in.close();
    }

    d.close();
  }

  public static void main(String[] args) throws IOException {
    long[] l = new long[128];
    for (int i = 0; i < 128; ++i) {
      l[i] = 1 + (i & 3);
    }
    l[0] = Integer.MAX_VALUE;
    System.out.println(Arrays.toString(l));
    ForUtil f = new ForUtil();
    byte[] b = new byte[128*4];
    ByteArrayDataOutput out = new ByteArrayDataOutput(b);
    f.encode(l, out);
    ByteArrayDataInput in = new ByteArrayDataInput(b);
    long[] restored = new long[128];
    f.decode(in, restored);
    System.out.println(Arrays.toString(restored));
  }

}
