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
package org.apache.lucene.util.bkd;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.BytesRef;

/** Handles intersection of an multi-dimensional shape in byte[] space with a block KD-tree previously written with {@link BKDWriter}.
 *
 * @lucene.experimental */

public final class BKDReader extends PointValues implements Accountable {
  // Packed array of byte[] holding all split values in the full binary tree:
  final int leafNodeOffset;
  final int numDataDims;
  final int numIndexDims;
  final int bytesPerDim;
  final int numLeaves;
  final IndexInput in;
  final int maxPointsInLeafNode;
  final long pointCount;
  final int docCount;
  final int version;
  protected final int packedBytesLength;
  protected final int packedIndexBytesLength;

  final byte[] index;
  final long[] leafBlockFPs;

  /** Caller must pre-seek the provided {@link IndexInput} to the index location that {@link BKDWriter#finish} returned */
  public BKDReader(IndexInput in) throws IOException {
    version = CodecUtil.checkHeader(in, BKDWriter.CODEC_NAME, BKDWriter.VERSION_START, BKDWriter.VERSION_CURRENT);
    numDataDims = in.readVInt();
    if (version >= BKDWriter.VERSION_SELECTIVE_INDEXING) {
      numIndexDims = in.readVInt();
    } else {
      numIndexDims = numDataDims;
    }
    maxPointsInLeafNode = in.readVInt();
    bytesPerDim = in.readVInt();
    packedBytesLength = numDataDims * bytesPerDim;
    packedIndexBytesLength = numIndexDims * bytesPerDim;

    // Read index:
    numLeaves = in.readVInt();
    assert numLeaves > 0;
    leafNodeOffset = numLeaves;
    
    pointCount = in.readVLong();
    docCount = in.readVInt();

    index = new byte[numLeaves * 4 * packedIndexBytesLength];
    in.readBytes(index, 0, index.length);
    assert Arrays.equals(Arrays.copyOfRange(index, 0, 2 * packedIndexBytesLength), new byte[2 * packedIndexBytesLength]);
    leafBlockFPs = new long[numLeaves];
    for (int i = 0; i < numLeaves; ++i) {
      leafBlockFPs[i] = in.readLong();
    }

    this.in = in;
  }

  long getMinLeafBlockFP() {
    return leafBlockFPs[0];
  }

  /** Used to walk the in-heap index. The format takes advantage of the limited
   *  access pattern to the BKD tree at search time, i.e. starting at the root
   *  node and recursing downwards one child at a time.
   *  @lucene.internal */
  public class IndexTree implements Cloneable {
    private int nodeID;
    // level is 1-based so that we can do level-1 w/o checking each time:
    private int level;
    // node bounds
    private final byte[] minPackedValue = new byte[packedIndexBytesLength];
    private final byte[] maxPackedValue = new byte[packedIndexBytesLength];
    // scratch value to return from getPackedValue:
    private final BytesRef scratch;

    IndexTree() {
      nodeID = 1;
      level = 1;

      readNodeData();
      scratch = new BytesRef();
      scratch.length = bytesPerDim;
    }      

    public void pushLeft() {
      nodeID *= 2;
      level++;
      readNodeData();
    }
    
    /** Clone, but you are not allowed to pop up past the point where the clone happened. */
    @Override
    public IndexTree clone() {
      IndexTree index = new IndexTree();
      index.nodeID = nodeID;
      index.level = level;
      System.arraycopy(minPackedValue, 0, index.minPackedValue, 0, packedIndexBytesLength);
      System.arraycopy(maxPackedValue, 0, index.maxPackedValue, 0, packedIndexBytesLength);
      return index;
    }
    
    public void pushRight() {
      nodeID = nodeID * 2 + 1;
      level++;
      readNodeData();
    }

    public void pop() {
      nodeID /= 2;
      level--;
      //System.out.println("  pop nodeID=" + nodeID);
    }

    public boolean isLeafNode() {
      return nodeID >= leafNodeOffset;
    }

    public boolean nodeExists() {
      return nodeID - leafNodeOffset < leafNodeOffset;
    }

    public int getNodeID() {
      return nodeID;
    }

    public byte[] getMinPackedValue() {
      return minPackedValue;
    }

    public byte[] getMaxPackedValue() {
      return maxPackedValue;
    }
    
    /** Only valid after pushLeft or pushRight, not pop! */
    public long getLeafBlockFP() {
      assert isLeafNode(): "nodeID=" + nodeID + " is not a leaf";
      return leafBlockFPs[nodeID - numLeaves];
    }

    /** Return the number of leaves below the current node. */
    public int getNumLeaves() {
      int leftMostLeafNode = nodeID;
      while (leftMostLeafNode < leafNodeOffset) {
        leftMostLeafNode = leftMostLeafNode * 2;
      }
      int rightMostLeafNode = nodeID;
      while (rightMostLeafNode < leafNodeOffset) {
        rightMostLeafNode = rightMostLeafNode * 2 + 1;
      }
      final int numLeaves;
      if (rightMostLeafNode >= leftMostLeafNode) {
        // both are on the same level
        numLeaves = rightMostLeafNode - leftMostLeafNode + 1;
      } else {
        // left is one level deeper than right
        numLeaves = rightMostLeafNode - leftMostLeafNode + 1 + leafNodeOffset;
      }
      assert numLeaves == getNumLeavesSlow(nodeID) : numLeaves + " " + getNumLeavesSlow(nodeID);
      return numLeaves;
    }

    // for assertions
    private int getNumLeavesSlow(int node) {
      if (node >= 2 * leafNodeOffset) {
        return 0;
      } else if (node >= leafNodeOffset) {
        return 1;
      } else {
        final int leftCount = getNumLeavesSlow(node * 2);
        final int rightCount = getNumLeavesSlow(node * 2 + 1);
        return leftCount + rightCount;
      }
    }

    private void readNodeData() {
      int address = nodeID * 2 * packedIndexBytesLength;
      System.arraycopy(index, address, minPackedValue, 0, packedIndexBytesLength);
      System.arraycopy(index, address + packedIndexBytesLength, maxPackedValue, 0, packedIndexBytesLength);
      for (int dim = 0; dim < numIndexDims; ++dim) {
        final int offset = dim * bytesPerDim;
        assert Arrays.compareUnsigned(minPackedValue, offset, offset + bytesPerDim, maxPackedValue, offset, offset + bytesPerDim) <= 0 : new BytesRef(minPackedValue) + " > " + new BytesRef(maxPackedValue) + " " + nodeID + " / " + dim;
      }
    }
  }

  /** Used to track all state for a single call to {@link #intersect}. */
  public static final class IntersectState {
    final IndexInput in;
    final BKDReaderDocIDSetIterator scratchIterator;
    final byte[] scratchDataPackedValue, scratchMinIndexPackedValue, scratchMaxIndexPackedValue;
    final int[] commonPrefixLengths;

    final IntersectVisitor visitor;
    public final IndexTree index;

    public IntersectState(IndexInput in, int numDims,
                          int packedBytesLength,
                          int packedIndexBytesLength,
                          int maxPointsInLeafNode,
                          IntersectVisitor visitor,
                          IndexTree indexVisitor) {
      this.in = in;
      this.visitor = visitor;
      this.commonPrefixLengths = new int[numDims];
      this.scratchIterator = new BKDReaderDocIDSetIterator(maxPointsInLeafNode);
      this.scratchDataPackedValue = new byte[packedBytesLength];
      this.scratchMinIndexPackedValue = new byte[packedIndexBytesLength];
      this.scratchMaxIndexPackedValue = new byte[packedIndexBytesLength];
      this.index = indexVisitor;
    }
  }

  @Override
  public void intersect(IntersectVisitor visitor) throws IOException {
    intersect(getIntersectState(visitor));
  }

  @Override
  public long estimatePointCount(IntersectVisitor visitor) {
    return estimatePointCount(getIntersectState(visitor));
  }

  /** Fast path: this is called when the query box fully encompasses all cells under this node. */
  private void addAll(IntersectState state, boolean grown) throws IOException {
    //System.out.println("R: addAll nodeID=" + nodeID);

    if (grown == false) {
      final long maxPointCount = (long) maxPointsInLeafNode * state.index.getNumLeaves();
      if (maxPointCount <= Integer.MAX_VALUE) { // could be >MAX_VALUE if there are more than 2B points in total
        state.visitor.grow((int) maxPointCount);
        grown = true;
      }
    }

    if (state.index.isLeafNode()) {
      assert grown;
      //System.out.println("ADDALL");
      if (state.index.nodeExists()) {
        visitDocIDs(state.in, state.index.getLeafBlockFP(), state.visitor);
      }
      // TODO: we can assert that the first value here in fact matches what the index claimed?
    } else {
      state.index.pushLeft();
      addAll(state, grown);
      state.index.pop();

      state.index.pushRight();
      addAll(state, grown);
      state.index.pop();
    }
  }

  /** Create a new {@link IntersectState} */
  public IntersectState getIntersectState(IntersectVisitor visitor) {
    IndexTree index = new IndexTree();
    return new IntersectState(in.clone(), numDataDims,
                              packedBytesLength,
                              packedIndexBytesLength,
                              maxPointsInLeafNode,
                              visitor,
                              index);
  }

  /** Visits all docIDs and packed values in a single leaf block */
  public void visitLeafBlockValues(IndexTree index, IntersectState state) throws IOException {

    // Leaf node; scan and filter all points in this block:
    int count = readDocIDs(state.in, index.getLeafBlockFP(), state.scratchIterator);

    // Again, this time reading values and checking with the visitor
    visitDocValues(state.commonPrefixLengths, state.scratchDataPackedValue, state.scratchMinIndexPackedValue, state.scratchMaxIndexPackedValue, state.in, state.scratchIterator, count, state.visitor);
  }

  private void visitDocIDs(IndexInput in, long blockFP, IntersectVisitor visitor) throws IOException {
    // Leaf node
    in.seek(blockFP);

    // How many points are stored in this leaf cell:
    int count = in.readVInt();
    // No need to call grow(), it has been called up-front

    DocIdsWriter.readInts(in, count, visitor);
  }

  int readDocIDs(IndexInput in, long blockFP, BKDReaderDocIDSetIterator iterator) throws IOException {
    in.seek(blockFP);

    // How many points are stored in this leaf cell:
    int count = in.readVInt();

    DocIdsWriter.readInts(in, count, iterator.docIDs);

    return count;
  }

  void visitDocValues(int[] commonPrefixLengths, byte[] scratchDataPackedValue, byte[] scratchMinIndexPackedValue, byte[] scratchMaxIndexPackedValue,
                      IndexInput in, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor) throws IOException {
    if (version >= BKDWriter.VERSION_LOW_CARDINALITY_LEAVES) {
      visitDocValuesWithCardinality(commonPrefixLengths, scratchDataPackedValue, scratchMinIndexPackedValue, scratchMaxIndexPackedValue, in, scratchIterator, count, visitor);
    } else {
      visitDocValuesNoCardinality(commonPrefixLengths, scratchDataPackedValue, scratchMinIndexPackedValue, scratchMaxIndexPackedValue, in, scratchIterator, count, visitor);
    }
  }

  void visitDocValuesNoCardinality(int[] commonPrefixLengths, byte[] scratchDataPackedValue, byte[] scratchMinIndexPackedValue, byte[] scratchMaxIndexPackedValue,
                      IndexInput in, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor) throws IOException {
    readCommonPrefixes(commonPrefixLengths, scratchDataPackedValue, in);

    if (numIndexDims != 1 && version >= BKDWriter.VERSION_LEAF_STORES_BOUNDS) {
      byte[] minPackedValue = scratchMinIndexPackedValue;
      System.arraycopy(scratchDataPackedValue, 0, minPackedValue, 0, packedIndexBytesLength);
      byte[] maxPackedValue = scratchMaxIndexPackedValue;
      // Copy common prefixes before reading adjusted box
      System.arraycopy(minPackedValue, 0, maxPackedValue, 0, packedIndexBytesLength);
      readMinMax(commonPrefixLengths, minPackedValue, maxPackedValue, in);

      // The index gives us range of values for each dimension, but the actual range of values
      // might be much more narrow than what the index told us, so we double check the relation
      // here, which is cheap yet might help figure out that the block either entirely matches
      // or does not match at all. This is especially more likely in the case that there are
      // multiple dimensions that have correlation, ie. splitting on one dimension also
      // significantly changes the range of values in another dimension.
      Relation r = visitor.compare(minPackedValue, maxPackedValue);
      if (r == Relation.CELL_OUTSIDE_QUERY) {
        return;
      }
      visitor.grow(count);

      if (r == Relation.CELL_INSIDE_QUERY) {
        for (int i = 0; i < count; ++i) {
          visitor.visit(scratchIterator.docIDs[i]);
        }
        return;
      }
    } else {
      visitor.grow(count);
    }


    int compressedDim = readCompressedDim(in);

    if (compressedDim == -1) {
      visitUniqueRawDocValues(scratchDataPackedValue, scratchIterator, count, visitor);
    } else {
      visitCompressedDocValues(commonPrefixLengths, scratchDataPackedValue, in, scratchIterator, count, visitor, compressedDim);
    }
  }

  void visitDocValuesWithCardinality(int[] commonPrefixLengths, byte[] scratchDataPackedValue, byte[] scratchMinIndexPackedValue, byte[] scratchMaxIndexPackedValue,
                                     IndexInput in, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor) throws IOException {

    readCommonPrefixes(commonPrefixLengths, scratchDataPackedValue, in);
    int compressedDim = readCompressedDim(in);
    if (compressedDim == -1) {
      // all values are the same
      visitor.grow(count);
      visitUniqueRawDocValues(scratchDataPackedValue, scratchIterator, count, visitor);
    } else {
      if (numIndexDims != 1) {
        byte[] minPackedValue = scratchMinIndexPackedValue;
        System.arraycopy(scratchDataPackedValue, 0, minPackedValue, 0, packedIndexBytesLength);
        byte[] maxPackedValue = scratchMaxIndexPackedValue;
        // Copy common prefixes before reading adjusted box
        System.arraycopy(minPackedValue, 0, maxPackedValue, 0, packedIndexBytesLength);
        readMinMax(commonPrefixLengths, minPackedValue, maxPackedValue, in);

        // The index gives us range of values for each dimension, but the actual range of values
        // might be much more narrow than what the index told us, so we double check the relation
        // here, which is cheap yet might help figure out that the block either entirely matches
        // or does not match at all. This is especially more likely in the case that there are
        // multiple dimensions that have correlation, ie. splitting on one dimension also
        // significantly changes the range of values in another dimension.
        Relation r = visitor.compare(minPackedValue, maxPackedValue);
        if (r == Relation.CELL_OUTSIDE_QUERY) {
          return;
        }
        visitor.grow(count);

        if (r == Relation.CELL_INSIDE_QUERY) {
          for (int i = 0; i < count; ++i) {
            visitor.visit(scratchIterator.docIDs[i]);
          }
          return;
        }
      } else {
        visitor.grow(count);
      }
      if (compressedDim == -2) {
        // low cardinality values
        visitSparseRawDocValues(commonPrefixLengths, scratchDataPackedValue, in, scratchIterator, count, visitor);
      } else {
        // high cardinality
        visitCompressedDocValues(commonPrefixLengths, scratchDataPackedValue, in, scratchIterator, count, visitor, compressedDim);
      }
    }
  }

  private void readMinMax(int[] commonPrefixLengths, byte[] minPackedValue, byte[] maxPackedValue, IndexInput in) throws IOException {
    for (int dim = 0; dim < numIndexDims; dim++) {
      int prefix = commonPrefixLengths[dim];
      in.readBytes(minPackedValue, dim * bytesPerDim + prefix, bytesPerDim - prefix);
      in.readBytes(maxPackedValue, dim * bytesPerDim + prefix, bytesPerDim - prefix);
    }
  }

  // read cardinality and point
  private void visitSparseRawDocValues(int[] commonPrefixLengths, byte[] scratchPackedValue, IndexInput in, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor) throws IOException {
    int i;
    for (i = 0; i < count;) {
      int length = in.readVInt();
      for(int dim = 0; dim < numDataDims; dim++) {
        int prefix = commonPrefixLengths[dim];
        in.readBytes(scratchPackedValue, dim*bytesPerDim + prefix, bytesPerDim - prefix);
      }
      scratchIterator.reset(i, length);
      visitor.visit(scratchIterator, scratchPackedValue);
      i += length;
    }
    if (i != count) {
      throw new CorruptIndexException("Sub blocks do not add up to the expected count: " + count + " != " + i, in);
    }
  }

  // point is under commonPrefix
  private void visitUniqueRawDocValues(byte[] scratchPackedValue, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor) throws IOException {
    scratchIterator.reset(0, count);
    visitor.visit(scratchIterator, scratchPackedValue);
  }

  private void visitCompressedDocValues(int[] commonPrefixLengths, byte[] scratchPackedValue, IndexInput in, BKDReaderDocIDSetIterator scratchIterator, int count, IntersectVisitor visitor, int compressedDim) throws IOException {
    // the byte at `compressedByteOffset` is compressed using run-length compression,
    // other suffix bytes are stored verbatim
    final int compressedByteOffset = compressedDim * bytesPerDim + commonPrefixLengths[compressedDim];
    commonPrefixLengths[compressedDim]++;
    int i;
    for (i = 0; i < count; ) {
      scratchPackedValue[compressedByteOffset] = in.readByte();
      final int runLen = Byte.toUnsignedInt(in.readByte());
      for (int j = 0; j < runLen; ++j) {
        for(int dim = 0; dim < numDataDims; dim++) {
          int prefix = commonPrefixLengths[dim];
          in.readBytes(scratchPackedValue, dim*bytesPerDim + prefix, bytesPerDim - prefix);
        }
        visitor.visit(scratchIterator.docIDs[i+j], scratchPackedValue);
      }
      i += runLen;
    }
    if (i != count) {
      throw new CorruptIndexException("Sub blocks do not add up to the expected count: " + count + " != " + i, in);
    }
  }

  private int readCompressedDim(IndexInput in) throws IOException {
    int compressedDim = in.readByte();
    if (compressedDim < -2 || compressedDim >= numDataDims || (version < BKDWriter.VERSION_LOW_CARDINALITY_LEAVES && compressedDim == -2)) {
      throw new CorruptIndexException("Got compressedDim="+compressedDim, in);
    }
    return compressedDim;
  }

  private void readCommonPrefixes(int[] commonPrefixLengths, byte[] scratchPackedValue, IndexInput in) throws IOException {
    for(int dim=0;dim<numDataDims;dim++) {
      int prefix = in.readVInt();
      commonPrefixLengths[dim] = prefix;
      if (prefix > 0) {
        in.readBytes(scratchPackedValue, dim*bytesPerDim, prefix);
      }
      //System.out.println("R: " + dim + " of " + numDims + " prefix=" + prefix);
    }
  }

  private void intersect(IntersectState state) throws IOException {

    /*
    System.out.println("\nR: intersect nodeID=" + state.index.getNodeID());
    for(int dim=0;dim<numDims;dim++) {
      System.out.println("  dim=" + dim + "\n    cellMin=" + new BytesRef(cellMinPacked, dim*bytesPerDim, bytesPerDim) + "\n    cellMax=" + new BytesRef(cellMaxPacked, dim*bytesPerDim, bytesPerDim));
    }
    */

    Relation r = state.visitor.compare(state.index.getMinPackedValue(), state.index.getMaxPackedValue());

    if (r == Relation.CELL_OUTSIDE_QUERY) {
      // This cell is fully outside of the query shape: stop recursing
    } else if (r == Relation.CELL_INSIDE_QUERY) {
      // This cell is fully inside of the query shape: recursively add all points in this cell without filtering
      addAll(state, false);
      // The cell crosses the shape boundary, or the cell fully contains the query, so we fall through and do full filtering:
    } else if (state.index.isLeafNode()) {
      
      // TODO: we can assert that the first value here in fact matches what the index claimed?
      
      // In the unbalanced case it's possible the left most node only has one child:
      if (state.index.nodeExists()) {
        // Leaf node; scan and filter all points in this block:
        int count = readDocIDs(state.in, state.index.getLeafBlockFP(), state.scratchIterator);

        // Again, this time reading values and checking with the visitor
        visitDocValues(state.commonPrefixLengths, state.scratchDataPackedValue, state.scratchMinIndexPackedValue, state.scratchMaxIndexPackedValue, state.in, state.scratchIterator, count, state.visitor);
      }

    } else {

      state.index.pushLeft();
      intersect(state);
      state.index.pop();
      state.index.pushRight();
      intersect(state);
      state.index.pop();

    }
  }

  private long estimatePointCount(IntersectState state) {

    /*
    System.out.println("\nR: intersect nodeID=" + state.index.getNodeID());
    for(int dim=0;dim<numDims;dim++) {
      System.out.println("  dim=" + dim + "\n    cellMin=" + new BytesRef(cellMinPacked, dim*bytesPerDim, bytesPerDim) + "\n    cellMax=" + new BytesRef(cellMaxPacked, dim*bytesPerDim, bytesPerDim));
    }
    */

    Relation r = state.visitor.compare(state.index.getMinPackedValue(), state.index.getMaxPackedValue());

    if (r == Relation.CELL_OUTSIDE_QUERY) {
      // This cell is fully outside of the query shape: stop recursing
      return 0L;
    } else if (r == Relation.CELL_INSIDE_QUERY) {
      return (long) maxPointsInLeafNode * state.index.getNumLeaves();
    } else if (state.index.isLeafNode()) {
      // Assume half the points matched
      return (maxPointsInLeafNode + 1) / 2;
    } else {

      state.index.pushLeft();
      long leftCost = estimatePointCount(state);
      state.index.pop();
      state.index.pushRight();
      long rightCost = estimatePointCount(state);
      state.index.pop();

      return leftCost + rightCost;
    }
  }

  @Override
  public long ramBytesUsed() {
    return index.length + leafBlockFPs.length * Long.SIZE;
  }

  @Override
  public byte[] getMinPackedValue() {
    return Arrays.copyOfRange(index, 2 * packedIndexBytesLength, 3 * packedIndexBytesLength);
  }

  @Override
  public byte[] getMaxPackedValue() {
    return Arrays.copyOfRange(index, 3 * packedIndexBytesLength, 4 * packedIndexBytesLength);
  }

  @Override
  public int getNumDataDimensions() {
    return numDataDims;
  }

  @Override
  public int getNumIndexDimensions() {
    return numIndexDims;
  }

  @Override
  public int getBytesPerDimension() {
    return bytesPerDim;
  }

  @Override
  public long size() {
    return pointCount;
  }

  @Override
  public int getDocCount() {
    return docCount;
  }

  public boolean isLeafNode(int nodeID) {
    return nodeID >= leafNodeOffset;
  }

  /**
   * Reusable {@link DocIdSetIterator} to handle low cardinality leaves. */
  protected static class BKDReaderDocIDSetIterator extends DocIdSetIterator {

    private int idx;
    private int length;
    private int offset;
    private int docID;
    final int[] docIDs;

    public BKDReaderDocIDSetIterator(int maxPointsInLeafNode) {
      this.docIDs = new int[maxPointsInLeafNode];
    }

    @Override
    public int docID() {
     return docID;
    }

    private void  reset(int offset, int length) {
      this.offset = offset;
      this.length = length;
      assert offset + length <= docIDs.length;
      this.docID = -1;
      this.idx = 0;
    }

    @Override
    public int nextDoc() throws IOException {
      if (idx == length) {
        docID = DocIdSetIterator.NO_MORE_DOCS;
      } else {
        docID = docIDs[offset + idx];
        idx++;
      }
      return docID;
    }

    @Override
    public int advance(int target) throws IOException {
      return slowAdvance(target);
    }

    @Override
    public long cost() {
      return length;
    }
  }
}
