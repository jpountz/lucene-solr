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

final class IntArray {

  final long[] longs;

  IntArray() {
    longs = new long[ForUtil.BLOCK_SIZE_IN_LONGS];
  }

  int get(int index) {
    int i = index >>> 1;
    int shift = ((index + 1) & 1) << 5;
    return (int) (longs[i] >>> shift);
  }

  void set(int index, int value) {
    int i = index >>> 1;
    int shift = (1 - (index & 1)) << 5;
    long mask = 0xffffffffL;
    longs[i] = (longs[i] & ~(mask << shift)) | (((long) value) << shift);
  }

}
