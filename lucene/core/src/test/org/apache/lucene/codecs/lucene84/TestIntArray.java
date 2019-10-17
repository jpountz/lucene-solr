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

import org.apache.lucene.util.LuceneTestCase;

public class TestIntArray extends LuceneTestCase {

  public void testBasics() {
    IntArray arr = new IntArray();
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      assertEquals(0, arr.get(i));
    }
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      arr.set(i, i + 1);
    }
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      assertEquals(i + 1, arr.get(i));
    }
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      arr.set(i, Integer.MAX_VALUE - i);
    }
    for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
      assertEquals(Integer.MAX_VALUE - i, arr.get(i));
    }
  }

}
