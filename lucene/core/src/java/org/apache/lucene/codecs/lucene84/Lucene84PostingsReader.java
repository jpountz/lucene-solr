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


import static org.apache.lucene.codecs.lucene84.ForUtil.BLOCK_SIZE;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.DOC_CODEC;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.MAX_SKIP_LEVELS;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.PAY_CODEC;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.POS_CODEC;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.TERMS_CODEC;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.VERSION_CURRENT;
import static org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.VERSION_START;

import java.io.IOException;

import org.apache.lucene.codecs.BlockTermState;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.PostingsReaderBase;
import org.apache.lucene.codecs.lucene84.Lucene84PostingsFormat.IntBlockTermState;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Impacts;
import org.apache.lucene.index.ImpactsEnum;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SlowImpactsEnum;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * Concrete class that reads docId(maybe frq,pos,offset,payloads) list
 * with postings format.
 *
 * @lucene.experimental
 */
public final class Lucene84PostingsReader extends PostingsReaderBase {

  private static final long BASE_RAM_BYTES_USED = RamUsageEstimator.shallowSizeOfInstance(Lucene84PostingsReader.class);

  private final IndexInput docIn;
  private final IndexInput posIn;
  private final IndexInput payIn;

  final ForUtil forUtil;
  private int version;

  /** Sole constructor. */
  public Lucene84PostingsReader(SegmentReadState state) throws IOException {
    boolean success = false;
    IndexInput docIn = null;
    IndexInput posIn = null;
    IndexInput payIn = null;
    
    // NOTE: these data files are too costly to verify checksum against all the bytes on open,
    // but for now we at least verify proper structure of the checksum footer: which looks
    // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
    // such as file truncation.
    
    String docName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene84PostingsFormat.DOC_EXTENSION);
    try {
      docIn = state.directory.openInput(docName, state.context);
      version = CodecUtil.checkIndexHeader(docIn, DOC_CODEC, VERSION_START, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
      forUtil = new ForUtil();
      CodecUtil.retrieveChecksum(docIn);

      if (state.fieldInfos.hasProx()) {
        String proxName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene84PostingsFormat.POS_EXTENSION);
        posIn = state.directory.openInput(proxName, state.context);
        CodecUtil.checkIndexHeader(posIn, POS_CODEC, version, version, state.segmentInfo.getId(), state.segmentSuffix);
        CodecUtil.retrieveChecksum(posIn);

        if (state.fieldInfos.hasPayloads() || state.fieldInfos.hasOffsets()) {
          String payName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, Lucene84PostingsFormat.PAY_EXTENSION);
          payIn = state.directory.openInput(payName, state.context);
          CodecUtil.checkIndexHeader(payIn, PAY_CODEC, version, version, state.segmentInfo.getId(), state.segmentSuffix);
          CodecUtil.retrieveChecksum(payIn);
        }
      }

      this.docIn = docIn;
      this.posIn = posIn;
      this.payIn = payIn;
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(docIn, posIn, payIn);
      }
    }
  }

  @Override
  public void init(IndexInput termsIn, SegmentReadState state) throws IOException {
    // Make sure we are talking to the matching postings writer
    CodecUtil.checkIndexHeader(termsIn, TERMS_CODEC, VERSION_START, VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
    final int indexBlockSize = termsIn.readVInt();
    if (indexBlockSize != BLOCK_SIZE) {
      throw new IllegalStateException("index-time BLOCK_SIZE (" + indexBlockSize + ") != read-time BLOCK_SIZE (" + BLOCK_SIZE + ")");
    }
  }

  /**
   * Read values that have been written using variable-length encoding instead of bit-packing.
   */
  static void readVIntBlock(IndexInput docIn, IntArray docBuffer,
      IntArray freqBuffer, int num, boolean indexHasFreq) throws IOException {
    if (indexHasFreq) {
      for(int i=0;i<num;i++) {
        final int code = docIn.readVInt();
        docBuffer.set(i, code >>> 1);
        if ((code & 1) != 0) {
          freqBuffer.set(i, 1);
        } else {
          freqBuffer.set(i, docIn.readVInt());
        }
      }
    } else {
      for(int i=0;i<num;i++) {
        docBuffer.set(i, docIn.readVInt());
      }
    }
  }

  @Override
  public BlockTermState newTermState() {
    return new IntBlockTermState();
  }

  @Override
  public void close() throws IOException {
    IOUtils.close(docIn, posIn, payIn);
  }

  @Override
  public void decodeTerm(long[] longs, DataInput in, FieldInfo fieldInfo, BlockTermState _termState, boolean absolute)
    throws IOException {
    final IntBlockTermState termState = (IntBlockTermState) _termState;
    final boolean fieldHasPositions = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
    final boolean fieldHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
    final boolean fieldHasPayloads = fieldInfo.hasPayloads();

    if (absolute) {
      termState.docStartFP = 0;
      termState.posStartFP = 0;
      termState.payStartFP = 0;
    }

    termState.docStartFP += longs[0];
    if (fieldHasPositions) {
      termState.posStartFP += longs[1];
      if (fieldHasOffsets || fieldHasPayloads) {
        termState.payStartFP += longs[2];
      }
    }
    if (termState.docFreq == 1) {
      termState.singletonDocID = in.readVInt();
    } else {
      termState.singletonDocID = -1;
    }
    if (fieldHasPositions) {
      if (termState.totalTermFreq > BLOCK_SIZE) {
        termState.lastPosBlockOffset = in.readVLong();
      } else {
        termState.lastPosBlockOffset = -1;
      }
    }
    if (termState.docFreq > BLOCK_SIZE) {
      termState.skipOffset = in.readVLong();
    } else {
      termState.skipOffset = -1;
    }
  }
    
  @Override
  public PostingsEnum postings(FieldInfo fieldInfo, BlockTermState termState, PostingsEnum reuse, int flags) throws IOException {
    
    boolean indexHasPositions = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;

    if (indexHasPositions == false || PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS) == false) {
      BlockDocsEnum docsEnum;
      if (reuse instanceof BlockDocsEnum) {
        docsEnum = (BlockDocsEnum) reuse;
        if (!docsEnum.canReuse(docIn, fieldInfo)) {
          docsEnum = new BlockDocsEnum(fieldInfo);
        }
      } else {
        docsEnum = new BlockDocsEnum(fieldInfo);
      }
      return docsEnum.reset((IntBlockTermState) termState, flags);
    } else {
      EverythingEnum everythingEnum;
      if (reuse instanceof EverythingEnum) {
        everythingEnum = (EverythingEnum) reuse;
        if (!everythingEnum.canReuse(docIn, fieldInfo)) {
          everythingEnum = new EverythingEnum(fieldInfo);
        }
      } else {
        everythingEnum = new EverythingEnum(fieldInfo);
      }
      return everythingEnum.reset((IntBlockTermState) termState, flags);
    }
  }

  @Override
  public ImpactsEnum impacts(FieldInfo fieldInfo, BlockTermState state, int flags) throws IOException {
    if (state.docFreq <= BLOCK_SIZE) {
      // no skip data
      return new SlowImpactsEnum(postings(fieldInfo, state, null, flags));
    }

    final boolean indexHasPositions = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
    final boolean indexHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
    final boolean indexHasPayloads = fieldInfo.hasPayloads();

    if (indexHasPositions &&
        PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS) &&
        (indexHasOffsets == false || PostingsEnum.featureRequested(flags, PostingsEnum.OFFSETS) == false) &&
        (indexHasPayloads == false || PostingsEnum.featureRequested(flags, PostingsEnum.PAYLOADS) == false)) {
      return new BlockImpactsPostingsEnum(fieldInfo, (IntBlockTermState) state);
    }

    return new BlockImpactsEverythingEnum(fieldInfo, (IntBlockTermState) state, flags);
  }

  final class BlockDocsEnum extends PostingsEnum {
    
    private final IntArray docDeltaBuffer = new IntArray();
    private final IntArray freqBuffer = new IntArray();

    private int docBufferUpto;

    private Lucene84SkipReader skipper;
    private boolean skipped;

    final IndexInput startDocIn;

    IndexInput docIn;
    final boolean indexHasFreq;
    final boolean indexHasPos;
    final boolean indexHasOffsets;
    final boolean indexHasPayloads;

    private int docFreq;                              // number of docs in this posting list
    private long totalTermFreq;                       // sum of freqBuffer in this posting list (or docFreq when omitted)
    private int docUpto;                              // how many docs we've read
    private int doc;                                  // doc we last read
    private int accum;                                // accumulator for doc deltas

    // Where this term's postings start in the .doc file:
    private long docTermStartFP;

    // Where this term's skip data starts (after
    // docTermStartFP) in the .doc file (or -1 if there is
    // no skip data for this term):
    private long skipOffset;

    // docID for next skip point, we won't use skipper if 
    // target docID is not larger than this
    private int nextSkipDoc;
    
    private boolean needsFreq; // true if the caller actually needs frequencies
    // as we read freqBuffer lazily, isFreqsRead shows if freqBuffer are read for the current block
    // always true when we don't have freqBuffer (indexHasFreq=false) or don't need freqBuffer (needsFreq=false)
    private boolean isFreqsRead;
    private int singletonDocID; // docid when there is a single pulsed posting, otherwise -1

    public BlockDocsEnum(FieldInfo fieldInfo) throws IOException {
      this.startDocIn = Lucene84PostingsReader.this.docIn;
      this.docIn = null;
      indexHasFreq = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
      indexHasPos = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
      indexHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      indexHasPayloads = fieldInfo.hasPayloads(); 
    }

    public boolean canReuse(IndexInput docIn, FieldInfo fieldInfo) {
      return docIn == startDocIn &&
        indexHasFreq == (fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) >= 0) &&
        indexHasPos == (fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0) &&
        indexHasPayloads == fieldInfo.hasPayloads();
    }
    
    public PostingsEnum reset(IntBlockTermState termState, int flags) throws IOException {
      docFreq = termState.docFreq;
      totalTermFreq = indexHasFreq ? termState.totalTermFreq : docFreq;
      docTermStartFP = termState.docStartFP;
      skipOffset = termState.skipOffset;
      singletonDocID = termState.singletonDocID;
      if (docFreq > 1) {
        if (docIn == null) {
          // lazy init
          docIn = startDocIn.clone();
        }
        docIn.seek(docTermStartFP);
      }

      doc = -1;
      this.needsFreq = PostingsEnum.featureRequested(flags, PostingsEnum.FREQS);
      this.isFreqsRead = true;
      if (indexHasFreq == false || needsFreq == false) {
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
          freqBuffer.set(i, 1);
        }
      }
      accum = 0;
      docUpto = 0;
      nextSkipDoc = BLOCK_SIZE - 1; // we won't skip if target is found in first block
      docBufferUpto = BLOCK_SIZE;
      skipped = false;
      return this;
    }

    @Override
    public int freq() throws IOException {
      if (isFreqsRead == false) {
        forUtil.decode(docIn, freqBuffer); // read freqBuffer for this block
        isFreqsRead = true;
      }
      return freqBuffer.get(docBufferUpto-1);
    }

    @Override
    public int nextPosition() throws IOException {
      return -1;
    }

    @Override
    public int startOffset() throws IOException {
      return -1;
    }

    @Override
    public int endOffset() throws IOException {
      return -1;
    }

    @Override
    public BytesRef getPayload() throws IOException {
      return null;
    }

    @Override
    public int docID() {
      return doc;
    }
    
    private void refillDocs() throws IOException {
      // Check if we skipped reading the previous block of freqBuffer, and if yes, position docIn after it
      if (isFreqsRead == false) {
        forUtil.skip(docIn);
        isFreqsRead = true;
      }
      
      final int left = docFreq - docUpto;
      assert left > 0;

      if (left >= BLOCK_SIZE) {
        forUtil.decode(docIn, docDeltaBuffer);

        if (indexHasFreq) {
          if (needsFreq) {
            isFreqsRead = false;
          } else {
            forUtil.skip(docIn); // skip over freqBuffer if we don't need them at all
          }
        }
      } else if (docFreq == 1) {
        docDeltaBuffer.set(0, singletonDocID);
        freqBuffer.set(0, (int) totalTermFreq);
      } else {
        // Read vInts:
        readVIntBlock(docIn, docDeltaBuffer, freqBuffer, left, indexHasFreq);
      }
      docBufferUpto = 0;
    }

    @Override
    public int nextDoc() throws IOException {
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        refillDocs(); // we don't need to load freqBuffer for now (will be loaded later if necessary)
      }

      accum += docDeltaBuffer.get(docBufferUpto);
      docUpto++;

      doc = accum;
      docBufferUpto++;
      return doc;
    }

    @Override
    public int advance(int target) throws IOException {
      // current skip docID < docIDs generated from current buffer <= next skip docID
      // we don't need to skip if target is buffered already
      if (docFreq > BLOCK_SIZE && target > nextSkipDoc) {

        if (skipper == null) {
          // Lazy init: first time this enum has ever been used for skipping
          skipper = new Lucene84SkipReader(docIn.clone(),
                                           MAX_SKIP_LEVELS,
                                           indexHasPos,
                                           indexHasOffsets,
                                           indexHasPayloads);
        }

        if (!skipped) {
          assert skipOffset != -1;
          // This is the first time this enum has skipped
          // since reset() was called; load the skip data:
          skipper.init(docTermStartFP+skipOffset, docTermStartFP, 0, 0, docFreq);
          skipped = true;
        }

        // always plus one to fix the result, since skip position in Lucene84SkipReader 
        // is a little different from MultiLevelSkipListReader
        final int newDocUpto = skipper.skipTo(target) + 1; 

        if (newDocUpto > docUpto) {
          // Skipper moved
          assert newDocUpto % BLOCK_SIZE == 0 : "got " + newDocUpto;
          docUpto = newDocUpto;

          // Force to read next block
          docBufferUpto = BLOCK_SIZE;
          accum = skipper.getDoc();               // actually, this is just lastSkipEntry
          docIn.seek(skipper.getDocPointer());    // now point to the block we want to search
          // even if freqBuffer were not read from the previous block, we will mark them as read,
          // as we don't need to skip the previous block freqBuffer in refillDocs,
          // as we have already positioned docIn where in needs to be.
          isFreqsRead = true;
        }
        // next time we call advance, this is used to 
        // foresee whether skipper is necessary.
        nextSkipDoc = skipper.getNextSkipDoc();
      }
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        refillDocs();
      }

      // Now scan... this is an inlined/pared down version
      // of nextDoc():
      while (true) {
        accum += docDeltaBuffer.get(docBufferUpto);
        docUpto++;

        if (accum >= target) {
          break;
        }
        docBufferUpto++;
        if (docUpto == docFreq) {
          return doc = NO_MORE_DOCS;
        }
      }

      docBufferUpto++;
      return doc = accum;
    }
    
    @Override
    public long cost() {
      return docFreq;
    }
  }

  // Also handles payloads + offsets
  final class EverythingEnum extends PostingsEnum {

    private final IntArray docDeltaBuffer = new IntArray();
    private final IntArray freqBuffer = new IntArray();
    private final IntArray posDeltaBuffer = new IntArray();

    private final IntArray payloadLengthBuffer;
    private final IntArray offsetStartDeltaBuffer;
    private final IntArray offsetLengthBuffer;

    private byte[] payloadBytes;
    private int payloadByteUpto;
    private int payloadLength;

    private int lastStartOffset;
    private int startOffset;
    private int endOffset;

    private int docBufferUpto;
    private int posBufferUpto;

    private Lucene84SkipReader skipper;
    private boolean skipped;

    final IndexInput startDocIn;

    IndexInput docIn;
    final IndexInput posIn;
    final IndexInput payIn;
    final BytesRef payload;

    final boolean indexHasOffsets;
    final boolean indexHasPayloads;

    private int docFreq;                              // number of docs in this posting list
    private long totalTermFreq;                       // number of positions in this posting list
    private int docUpto;                              // how many docs we've read
    private int doc;                                  // doc we last read
    private int accum;                                // accumulator for doc deltas
    private int freq;                                 // freq we last read
    private int position;                             // current position

    // how many positions "behind" we are; nextPosition must
    // skip these to "catch up":
    private int posPendingCount;

    // Lazy pos seek: if != -1 then we must seek to this FP
    // before reading positions:
    private long posPendingFP;

    // Lazy pay seek: if != -1 then we must seek to this FP
    // before reading payloads/offsets:
    private long payPendingFP;

    // Where this term's postings start in the .doc file:
    private long docTermStartFP;

    // Where this term's postings start in the .pos file:
    private long posTermStartFP;

    // Where this term's payloads/offsets start in the .pay
    // file:
    private long payTermStartFP;

    // File pointer where the last (vInt encoded) pos delta
    // block is.  We need this to know whether to bulk
    // decode vs vInt decode the block:
    private long lastPosBlockFP;

    // Where this term's skip data starts (after
    // docTermStartFP) in the .doc file (or -1 if there is
    // no skip data for this term):
    private long skipOffset;

    private int nextSkipDoc;

    private boolean needsOffsets; // true if we actually need offsets
    private boolean needsPayloads; // true if we actually need payloads
    private int singletonDocID; // docid when there is a single pulsed posting, otherwise -1

    public EverythingEnum(FieldInfo fieldInfo) throws IOException {
      indexHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      indexHasPayloads = fieldInfo.hasPayloads();

      this.startDocIn = Lucene84PostingsReader.this.docIn;
      this.docIn = null;
      this.posIn = Lucene84PostingsReader.this.posIn.clone();
      if (indexHasOffsets || indexHasPayloads) {
        this.payIn = Lucene84PostingsReader.this.payIn.clone();
      } else {
        this.payIn = null;
      }
      if (indexHasOffsets) {
        offsetStartDeltaBuffer = new IntArray();
        offsetLengthBuffer = new IntArray();
      } else {
        offsetStartDeltaBuffer = null;
        offsetLengthBuffer = null;
        startOffset = -1;
        endOffset = -1;
      }

      if (indexHasPayloads) {
        payloadLengthBuffer = new IntArray();
        payloadBytes = new byte[128];
        payload = new BytesRef();
      } else {
        payloadLengthBuffer = null;
        payloadBytes = null;
        payload = null;
      }
    }

    public boolean canReuse(IndexInput docIn, FieldInfo fieldInfo) {
      return docIn == startDocIn &&
        indexHasOffsets == (fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0) &&
        indexHasPayloads == fieldInfo.hasPayloads();
    }

    public EverythingEnum reset(IntBlockTermState termState, int flags) throws IOException {
      docFreq = termState.docFreq;
      docTermStartFP = termState.docStartFP;
      posTermStartFP = termState.posStartFP;
      payTermStartFP = termState.payStartFP;
      skipOffset = termState.skipOffset;
      totalTermFreq = termState.totalTermFreq;
      singletonDocID = termState.singletonDocID;
      if (docFreq > 1) {
        if (docIn == null) {
          // lazy init
          docIn = startDocIn.clone();
        }
        docIn.seek(docTermStartFP);
      }
      posPendingFP = posTermStartFP;
      payPendingFP = payTermStartFP;
      posPendingCount = 0;
      if (termState.totalTermFreq < BLOCK_SIZE) {
        lastPosBlockFP = posTermStartFP;
      } else if (termState.totalTermFreq == BLOCK_SIZE) {
        lastPosBlockFP = -1;
      } else {
        lastPosBlockFP = posTermStartFP + termState.lastPosBlockOffset;
      }

      this.needsOffsets = PostingsEnum.featureRequested(flags, PostingsEnum.OFFSETS);
      this.needsPayloads = PostingsEnum.featureRequested(flags, PostingsEnum.PAYLOADS);

      doc = -1;
      accum = 0;
      docUpto = 0;
      if (docFreq > BLOCK_SIZE) {
        nextSkipDoc = BLOCK_SIZE - 1; // we won't skip if target is found in first block
      } else {
        nextSkipDoc = NO_MORE_DOCS; // not enough docs for skipping
      }
      docBufferUpto = BLOCK_SIZE;
      skipped = false;
      return this;
    }

    @Override
    public int freq() throws IOException {
      return freq;
    }

    @Override
    public int docID() {
      return doc;
    }

    private void refillDocs() throws IOException {
      final int left = docFreq - docUpto;
      assert left > 0;

      if (left >= BLOCK_SIZE) {
        forUtil.decode(docIn, docDeltaBuffer);
        forUtil.decode(docIn, freqBuffer);
      } else if (docFreq == 1) {
        docDeltaBuffer.set(0, singletonDocID);
        freqBuffer.set(0, (int) totalTermFreq);
      } else {
        readVIntBlock(docIn, docDeltaBuffer, freqBuffer, left, true);
      }
      docBufferUpto = 0;
    }

    private void refillPositions() throws IOException {
      if (posIn.getFilePointer() == lastPosBlockFP) {
        final int count = (int) (totalTermFreq % BLOCK_SIZE);
        int payloadLength = 0;
        int offsetLength = 0;
        payloadByteUpto = 0;
        for(int i=0;i<count;i++) {
          int code = posIn.readVInt();
          if (indexHasPayloads) {
            if ((code & 1) != 0) {
              payloadLength = posIn.readVInt();
            }
            payloadLengthBuffer.set(i, payloadLength);
            posDeltaBuffer.set(i, code >>> 1);
            if (payloadLength != 0) {
              if (payloadByteUpto + payloadLength > payloadBytes.length) {
                payloadBytes = ArrayUtil.grow(payloadBytes, payloadByteUpto + payloadLength);
              }
              posIn.readBytes(payloadBytes, payloadByteUpto, payloadLength);
              payloadByteUpto += payloadLength;
            }
          } else {
            posDeltaBuffer.set(i, code);
          }

          if (indexHasOffsets) {
            int deltaCode = posIn.readVInt();
            if ((deltaCode & 1) != 0) {
              offsetLength = posIn.readVInt();
            }
            offsetStartDeltaBuffer.set(i, deltaCode >>> 1);
            offsetLengthBuffer.set(i, offsetLength);
          }
        }
        payloadByteUpto = 0;
      } else {
        forUtil.decode(posIn, posDeltaBuffer);

        if (indexHasPayloads) {
          if (needsPayloads) {
            forUtil.decode(payIn, payloadLengthBuffer);
            int numBytes = payIn.readVInt();

            if (numBytes > payloadBytes.length) {
              payloadBytes = ArrayUtil.grow(payloadBytes, numBytes);
            }
            payIn.readBytes(payloadBytes, 0, numBytes);
          } else {
            // this works, because when writing a vint block we always force the first length to be written
            forUtil.skip(payIn); // skip over lengths
            int numBytes = payIn.readVInt(); // read length of payloadBytes
            payIn.seek(payIn.getFilePointer() + numBytes); // skip over payloadBytes
          }
          payloadByteUpto = 0;
        }

        if (indexHasOffsets) {
          if (needsOffsets) {
            forUtil.decode(payIn, offsetStartDeltaBuffer);
            forUtil.decode(payIn, offsetLengthBuffer);
          } else {
            // this works, because when writing a vint block we always force the first length to be written
            forUtil.skip(payIn); // skip over starts
            forUtil.skip(payIn); // skip over lengths
          }
        }
      }
    }

    @Override
    public int nextDoc() throws IOException {
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        refillDocs();
      }

      accum += docDeltaBuffer.get(docBufferUpto);
      freq = freqBuffer.get(docBufferUpto);
      posPendingCount += freq;
      docBufferUpto++;
      docUpto++;

      doc = accum;
      position = 0;
      lastStartOffset = 0;
      return doc;
    }

    @Override
    public int advance(int target) throws IOException {
      // TODO: make frq block load lazy/skippable

      if (target > nextSkipDoc) {
        if (skipper == null) {
          // Lazy init: first time this enum has ever been used for skipping
          skipper = new Lucene84SkipReader(docIn.clone(),
                                        MAX_SKIP_LEVELS,
                                        true,
                                        indexHasOffsets,
                                        indexHasPayloads);
        }

        if (!skipped) {
          assert skipOffset != -1;
          // This is the first time this enum has skipped
          // since reset() was called; load the skip data:
          skipper.init(docTermStartFP+skipOffset, docTermStartFP, posTermStartFP, payTermStartFP, docFreq);
          skipped = true;
        }

        final int newDocUpto = skipper.skipTo(target) + 1;

        if (newDocUpto > docUpto) {
          // Skipper moved
          assert newDocUpto % BLOCK_SIZE == 0 : "got " + newDocUpto;
          docUpto = newDocUpto;

          // Force to read next block
          docBufferUpto = BLOCK_SIZE;
          accum = skipper.getDoc();
          docIn.seek(skipper.getDocPointer());
          posPendingFP = skipper.getPosPointer();
          payPendingFP = skipper.getPayPointer();
          posPendingCount = skipper.getPosBufferUpto();
          lastStartOffset = 0; // new document
          payloadByteUpto = skipper.getPayloadByteUpto();
        }
        nextSkipDoc = skipper.getNextSkipDoc();
      }
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        refillDocs();
      }

      // Now scan:
      while (true) {
        accum += docDeltaBuffer.get(docBufferUpto);
        freq = freqBuffer.get(docBufferUpto);
        posPendingCount += freq;
        docBufferUpto++;
        docUpto++;

        if (accum >= target) {
          break;
        }
        if (docUpto == docFreq) {
          return doc = NO_MORE_DOCS;
        }
      }

      position = 0;
      lastStartOffset = 0;
      return doc = accum;
    }

    // TODO: in theory we could avoid loading frq block
    // when not needed, ie, use skip data to load how far to
    // seek the pos pointer ... instead of having to load frq
    // blocks only to sum up how many positions to skip
    private void skipPositions() throws IOException {
      // Skip positions now:
      int toSkip = posPendingCount - freq;
      // if (DEBUG) {
      //   System.out.println("      FPR.skipPositions: toSkip=" + toSkip);
      // }

      final int leftInBlock = BLOCK_SIZE - posBufferUpto;
      if (toSkip < leftInBlock) {
        int end = posBufferUpto + toSkip;
        while(posBufferUpto < end) {
          if (indexHasPayloads) {
            payloadByteUpto += payloadLengthBuffer.get(posBufferUpto);
          }
          posBufferUpto++;
        }
      } else {
        toSkip -= leftInBlock;
        while(toSkip >= BLOCK_SIZE) {
          assert posIn.getFilePointer() != lastPosBlockFP;
          forUtil.skip(posIn);

          if (indexHasPayloads) {
            // Skip payloadLength block:
            forUtil.skip(payIn);

            // Skip payloadBytes block:
            int numBytes = payIn.readVInt();
            payIn.seek(payIn.getFilePointer() + numBytes);
          }

          if (indexHasOffsets) {
            forUtil.skip(payIn);
            forUtil.skip(payIn);
          }
          toSkip -= BLOCK_SIZE;
        }
        refillPositions();
        payloadByteUpto = 0;
        posBufferUpto = 0;
        while(posBufferUpto < toSkip) {
          if (indexHasPayloads) {
            payloadByteUpto += payloadLengthBuffer.get(posBufferUpto);
          }
          posBufferUpto++;
        }
      }

      position = 0;
      lastStartOffset = 0;
    }

    @Override
    public int nextPosition() throws IOException {
      assert posPendingCount > 0;

      if (posPendingFP != -1) {
        posIn.seek(posPendingFP);
        posPendingFP = -1;

        if (payPendingFP != -1 && payIn != null) {
          payIn.seek(payPendingFP);
          payPendingFP = -1;
        }

        // Force buffer refill:
        posBufferUpto = BLOCK_SIZE;
      }

      if (posPendingCount > freq) {
        skipPositions();
        posPendingCount = freq;
      }

      if (posBufferUpto == BLOCK_SIZE) {
        refillPositions();
        posBufferUpto = 0;
      }
      position += posDeltaBuffer.get(posBufferUpto);

      if (indexHasPayloads) {
        payloadLength = payloadLengthBuffer.get(posBufferUpto);
        payload.bytes = payloadBytes;
        payload.offset = payloadByteUpto;
        payload.length = payloadLength;
        payloadByteUpto += payloadLength;
      }

      if (indexHasOffsets) {
        startOffset = lastStartOffset + offsetStartDeltaBuffer.get(posBufferUpto);
        endOffset = startOffset + offsetLengthBuffer.get(posBufferUpto);;
        lastStartOffset = startOffset;
      }

      posBufferUpto++;
      posPendingCount--;
      return position;
    }

    @Override
    public int startOffset() {
      return startOffset;
    }

    @Override
    public int endOffset() {
      return endOffset;
    }

    @Override
    public BytesRef getPayload() {
      if (payloadLength == 0) {
        return null;
      } else {
        return payload;
      }
    }

    @Override
    public long cost() {
      return docFreq;
    }
  }

  final class BlockImpactsPostingsEnum extends ImpactsEnum {

    private final IntArray docDeltaBuffer = new IntArray();
    private final IntArray freqBuffer = new IntArray();
    private final IntArray posDeltaBuffer = new IntArray();

    private int docBufferUpto;
    private int posBufferUpto;

    private final Lucene84ScoreSkipReader skipper;

    final IndexInput docIn;
    final IndexInput posIn;

    final boolean indexHasOffsets;
    final boolean indexHasPayloads;

    private int docFreq;                              // number of docs in this posting list
    private long totalTermFreq;                       // number of positions in this posting list
    private int docUpto;                              // how many docs we've read
    private int doc;                                  // doc we last read
    private int accum;                                // accumulator for doc deltas
    private int freq;                                 // freq we last read
    private int position;                             // current position

    // how many positions "behind" we are; nextPosition must
    // skip these to "catch up":
    private int posPendingCount;

    // Lazy pos seek: if != -1 then we must seek to this FP
    // before reading positions:
    private long posPendingFP;

    // Where this term's postings start in the .doc file:
    private long docTermStartFP;

    // Where this term's postings start in the .pos file:
    private long posTermStartFP;

    // Where this term's payloads/offsets start in the .pay
    // file:
    private long payTermStartFP;

    // File pointer where the last (vInt encoded) pos delta
    // block is.  We need this to know whether to bulk
    // decode vs vInt decode the block:
    private long lastPosBlockFP;

    private int nextSkipDoc = -1;

    private long seekTo = -1;

    public BlockImpactsPostingsEnum(FieldInfo fieldInfo, IntBlockTermState termState) throws IOException {
      indexHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      indexHasPayloads = fieldInfo.hasPayloads();

      this.docIn = Lucene84PostingsReader.this.docIn.clone();

      this.posIn = Lucene84PostingsReader.this.posIn.clone();

      docFreq = termState.docFreq;
      docTermStartFP = termState.docStartFP;
      posTermStartFP = termState.posStartFP;
      payTermStartFP = termState.payStartFP;
      totalTermFreq = termState.totalTermFreq;
      docIn.seek(docTermStartFP);
      posPendingFP = posTermStartFP;
      posPendingCount = 0;
      if (termState.totalTermFreq < BLOCK_SIZE) {
        lastPosBlockFP = posTermStartFP;
      } else if (termState.totalTermFreq == BLOCK_SIZE) {
        lastPosBlockFP = -1;
      } else {
        lastPosBlockFP = posTermStartFP + termState.lastPosBlockOffset;
      }

      doc = -1;
      accum = 0;
      docUpto = 0;
      docBufferUpto = BLOCK_SIZE;

      skipper = new Lucene84ScoreSkipReader(docIn.clone(),
          MAX_SKIP_LEVELS,
          true,
          indexHasOffsets,
          indexHasPayloads);
      skipper.init(docTermStartFP+termState.skipOffset, docTermStartFP, posTermStartFP, payTermStartFP, docFreq);
    }

    @Override
    public int freq() throws IOException {
      return freq;
    }

    @Override
    public int docID() {
      return doc;
    }

    private void refillDocs() throws IOException {
      final int left = docFreq - docUpto;
      assert left > 0;

      if (left >= BLOCK_SIZE) {
        forUtil.decode(docIn, docDeltaBuffer);
        forUtil.decode(docIn, freqBuffer);
      } else {
        readVIntBlock(docIn, docDeltaBuffer, freqBuffer, left, true);
      }
      docBufferUpto = 0;
    }

    private void refillPositions() throws IOException {
      if (posIn.getFilePointer() == lastPosBlockFP) {
        final int count = (int) (totalTermFreq % BLOCK_SIZE);
        int payloadLength = 0;
        for(int i=0;i<count;i++) {
          int code = posIn.readVInt();
          if (indexHasPayloads) {
            if ((code & 1) != 0) {
              payloadLength = posIn.readVInt();
            }
            posDeltaBuffer.set(i, code >>> 1);
            if (payloadLength != 0) {
              posIn.seek(posIn.getFilePointer() + payloadLength);
            }
          } else {
            posDeltaBuffer.set(i, code);
          }
          if (indexHasOffsets) {
            if ((posIn.readVInt() & 1) != 0) {
              // offset length changed
              posIn.readVInt();
            }
          }
        }
      } else {
        forUtil.decode(posIn, posDeltaBuffer);
      }
    }

    @Override
    public void advanceShallow(int target) throws IOException {
      if (target > nextSkipDoc) {
        // always plus one to fix the result, since skip position in Lucene84SkipReader
        // is a little different from MultiLevelSkipListReader
        final int newDocUpto = skipper.skipTo(target) + 1;

        if (newDocUpto > docUpto) {
          // Skipper moved
          assert newDocUpto % BLOCK_SIZE == 0 : "got " + newDocUpto;
          docUpto = newDocUpto;

          // Force to read next block
          docBufferUpto = BLOCK_SIZE;
          accum = skipper.getDoc();
          posPendingFP = skipper.getPosPointer();
          posPendingCount = skipper.getPosBufferUpto();
          seekTo = skipper.getDocPointer();       // delay the seek
        }
        // next time we call advance, this is used to 
        // foresee whether skipper is necessary.
        nextSkipDoc = skipper.getNextSkipDoc();
      }
      assert nextSkipDoc >= target;
    }

    @Override
    public Impacts getImpacts() throws IOException {
      advanceShallow(doc);
      return skipper.getImpacts();
    }

    @Override
    public int nextDoc() throws IOException {
      return advance(doc + 1);
    }

    @Override
    public int advance(int target) throws IOException {
      if (target > nextSkipDoc) {
        advanceShallow(target);
      }
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        if (seekTo >= 0) {
          docIn.seek(seekTo);
          seekTo = -1;
        }
        refillDocs();
      }

      // Now scan:
      while (true) {
        accum += docDeltaBuffer.get(docBufferUpto);
        freq = freqBuffer.get(docBufferUpto);;
        posPendingCount += freq;
        docBufferUpto++;
        docUpto++;

        if (accum >= target) {
          break;
        }
        if (docUpto == docFreq) {
          return doc = NO_MORE_DOCS;
        }
      }
      position = 0;

      return doc = accum;
    }

    // TODO: in theory we could avoid loading frq block
    // when not needed, ie, use skip data to load how far to
    // seek the pos pointer ... instead of having to load frq
    // blocks only to sum up how many positions to skip
    private void skipPositions() throws IOException {
      // Skip positions now:
      int toSkip = posPendingCount - freq;

      final int leftInBlock = BLOCK_SIZE - posBufferUpto;
      if (toSkip < leftInBlock) {
        posBufferUpto += toSkip;
      } else {
        toSkip -= leftInBlock;
        while(toSkip >= BLOCK_SIZE) {
          assert posIn.getFilePointer() != lastPosBlockFP;
          forUtil.skip(posIn);
          toSkip -= BLOCK_SIZE;
        }
        refillPositions();
        posBufferUpto = toSkip;
      }

      position = 0;
    }

    @Override
    public int nextPosition() throws IOException {
      assert posPendingCount > 0;

      if (posPendingFP != -1) {
        posIn.seek(posPendingFP);
        posPendingFP = -1;

        // Force buffer refill:
        posBufferUpto = BLOCK_SIZE;
      }

      if (posPendingCount > freq) {
        skipPositions();
        posPendingCount = freq;
      }

      if (posBufferUpto == BLOCK_SIZE) {
        refillPositions();
        posBufferUpto = 0;
      }
      position += posDeltaBuffer.get(posBufferUpto++);

      posPendingCount--;
      return position;
    }

    @Override
    public int startOffset() {
      return -1;
    }

    @Override
    public int endOffset() {
      return -1;
    }

    @Override
    public BytesRef getPayload() {
      return null;
    }

    @Override
    public long cost() {
      return docFreq;
    }

  }

  final class BlockImpactsEverythingEnum extends ImpactsEnum {

    private final IntArray docDeltaBuffer = new IntArray();
    private final IntArray freqBuffer = new IntArray();
    private final IntArray posDeltaBuffer = new IntArray();

    private final IntArray payloadLengthBuffer;
    private final IntArray offsetStartDeltaBuffer;
    private final IntArray offsetLengthBuffer;

    private byte[] payloadBytes;
    private int payloadByteUpto;
    private int payloadLength;

    private int lastStartOffset;
    private int startOffset = -1;
    private int endOffset = -1;

    private int docBufferUpto;
    private int posBufferUpto;

    private final Lucene84ScoreSkipReader skipper;

    final IndexInput docIn;
    final IndexInput posIn;
    final IndexInput payIn;
    final BytesRef payload;

    final boolean indexHasFreq;
    final boolean indexHasPos;
    final boolean indexHasOffsets;
    final boolean indexHasPayloads;

    private int docFreq;                              // number of docs in this posting list
    private long totalTermFreq;                       // number of positions in this posting list
    private int docUpto;                              // how many docs we've read
    private int posDocUpTo;                           // for how many docs we've read positions, offsets, and payloads
    private int doc;                                  // doc we last read
    private int accum;                                // accumulator for doc deltas
    private int position;                             // current position

    // how many positions "behind" we are; nextPosition must
    // skip these to "catch up":
    private int posPendingCount;

    // Lazy pos seek: if != -1 then we must seek to this FP
    // before reading positions:
    private long posPendingFP;

    // Lazy pay seek: if != -1 then we must seek to this FP
    // before reading payloads/offsets:
    private long payPendingFP;

    // Where this term's postings start in the .doc file:
    private long docTermStartFP;

    // Where this term's postings start in the .pos file:
    private long posTermStartFP;

    // Where this term's payloads/offsets start in the .pay
    // file:
    private long payTermStartFP;

    // File pointer where the last (vInt encoded) pos delta
    // block is.  We need this to know whether to bulk
    // decode vs vInt decode the block:
    private long lastPosBlockFP;

    private int nextSkipDoc = -1;
    
    private final boolean needsPositions;
    private final boolean needsOffsets; // true if we actually need offsets
    private final boolean needsPayloads; // true if we actually need payloads

    private boolean isFreqsRead; // shows if freqBuffer for the current doc block are read into freqBuffer
    
    private long seekTo = -1;
    
    public BlockImpactsEverythingEnum(FieldInfo fieldInfo, IntBlockTermState termState, int flags) throws IOException {
      indexHasFreq = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS) >= 0;
      indexHasPos = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0;
      indexHasOffsets = fieldInfo.getIndexOptions().compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS) >= 0;
      indexHasPayloads = fieldInfo.hasPayloads();
      
      needsPositions = PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS);
      needsOffsets = PostingsEnum.featureRequested(flags, PostingsEnum.OFFSETS);
      needsPayloads = PostingsEnum.featureRequested(flags, PostingsEnum.PAYLOADS);
      
      this.docIn = Lucene84PostingsReader.this.docIn.clone();

      if (indexHasPos && needsPositions) {
        this.posIn = Lucene84PostingsReader.this.posIn.clone();
      } else {
        this.posIn = null;
      }
      
      if ((indexHasOffsets && needsOffsets) || (indexHasPayloads && needsPayloads)) {
        this.payIn = Lucene84PostingsReader.this.payIn.clone();
      } else {
        this.payIn = null;
      }
      
      if (indexHasOffsets) {
        offsetStartDeltaBuffer = new IntArray();
        offsetLengthBuffer = new IntArray();
      } else {
        offsetStartDeltaBuffer = null;
        offsetLengthBuffer = null;
        startOffset = -1;
        endOffset = -1;
      }

      if (indexHasPayloads) {
        payloadLengthBuffer = new IntArray();
        payloadBytes = new byte[128];
        payload = new BytesRef();
      } else {
        payloadLengthBuffer = null;
        payloadBytes = null;
        payload = null;
      }

      docFreq = termState.docFreq;
      docTermStartFP = termState.docStartFP;
      posTermStartFP = termState.posStartFP;
      payTermStartFP = termState.payStartFP;
      totalTermFreq = termState.totalTermFreq;
      docIn.seek(docTermStartFP);
      posPendingFP = posTermStartFP;
      payPendingFP = payTermStartFP;
      posPendingCount = 0;
      if (termState.totalTermFreq < BLOCK_SIZE) {
        lastPosBlockFP = posTermStartFP;
      } else if (termState.totalTermFreq == BLOCK_SIZE) {
        lastPosBlockFP = -1;
      } else {
        lastPosBlockFP = posTermStartFP + termState.lastPosBlockOffset;
      }

      doc = -1;
      accum = 0;
      docUpto = 0;
      posDocUpTo = 0;
      isFreqsRead = true;
      docBufferUpto = BLOCK_SIZE;

      skipper = new Lucene84ScoreSkipReader(docIn.clone(),
          MAX_SKIP_LEVELS,
          indexHasPos,
          indexHasOffsets,
          indexHasPayloads);
      skipper.init(docTermStartFP+termState.skipOffset, docTermStartFP, posTermStartFP, payTermStartFP, docFreq);

      if (indexHasFreq == false) {
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
          freqBuffer.set(i, 1);
        }
      }
    }
    
    @Override
    public int freq() throws IOException {
      if (indexHasFreq && (isFreqsRead == false)) {
        forUtil.decode(docIn, freqBuffer); // read freqBuffer for this block
        isFreqsRead = true;
      }
      return freqBuffer.get(docBufferUpto-1);
    }

    @Override
    public int docID() {
      return doc;
    }

    private void refillDocs() throws IOException {
      if (indexHasFreq) {
        if (isFreqsRead == false) { // previous freq block was not read
          // check if we need to load the previous freq block to catch up on positions or we can skip it
          if (indexHasPos && needsPositions && (posDocUpTo < docUpto)) {
            forUtil.decode(docIn, freqBuffer); // load the previous freq block
          } else {
            forUtil.skip(docIn); // skip it
          }
          isFreqsRead = true;
        }
        if (indexHasPos && needsPositions) {
          while (posDocUpTo < docUpto) { // catch on positions, bring posPendingCount upto the current doc
            posPendingCount += freqBuffer.get(docBufferUpto - (docUpto - posDocUpTo));
            posDocUpTo++;
          }
        }
      }

      final int left = docFreq - docUpto;
      assert left > 0;

      if (left >= BLOCK_SIZE) {
        forUtil.decode(docIn, docDeltaBuffer);
        if (indexHasFreq) {
          isFreqsRead = false; // freq block will be loaded lazily when necessary, we don't load it here
        }
      } else {
        readVIntBlock(docIn, docDeltaBuffer, freqBuffer, left, indexHasFreq);
      }
      docBufferUpto = 0;
    }
    
    private void refillPositions() throws IOException {
      if (posIn.getFilePointer() == lastPosBlockFP) {
        final int count = (int) (totalTermFreq % BLOCK_SIZE);
        int payloadLength = 0;
        int offsetLength = 0;
        payloadByteUpto = 0;
        for(int i=0;i<count;i++) {
          int code = posIn.readVInt();
          if (indexHasPayloads) {
            if ((code & 1) != 0) {
              payloadLength = posIn.readVInt();
            }
            payloadLengthBuffer.set(i, payloadLength);
            posDeltaBuffer.set(i, code >>> 1);
            if (payloadLength != 0) {
              if (payloadByteUpto + payloadLength > payloadBytes.length) {
                payloadBytes = ArrayUtil.grow(payloadBytes, payloadByteUpto + payloadLength);
              }
              posIn.readBytes(payloadBytes, payloadByteUpto, payloadLength);
              payloadByteUpto += payloadLength;
            }
          } else {
            posDeltaBuffer.set(i, code);
          }

          if (indexHasOffsets) {
            int deltaCode = posIn.readVInt();
            if ((deltaCode & 1) != 0) {
              offsetLength = posIn.readVInt();
            }
            offsetStartDeltaBuffer.set(i, deltaCode >>> 1);
            offsetLengthBuffer.set(i, offsetLength);
          }
        }
        payloadByteUpto = 0;
      } else {
        forUtil.decode(posIn, posDeltaBuffer);

        if (indexHasPayloads && payIn != null) {
          if (needsPayloads) {
            forUtil.decode(payIn, payloadLengthBuffer);
            int numBytes = payIn.readVInt();

            if (numBytes > payloadBytes.length) {
              payloadBytes = ArrayUtil.grow(payloadBytes, numBytes);
            }
            payIn.readBytes(payloadBytes, 0, numBytes);
          } else {
            // this works, because when writing a vint block we always force the first length to be written
            forUtil.skip(payIn); // skip over lengths
            int numBytes = payIn.readVInt(); // read length of payloadBytes
            payIn.seek(payIn.getFilePointer() + numBytes); // skip over payloadBytes
          }
          payloadByteUpto = 0;
        }

        if (indexHasOffsets && payIn != null) {
          if (needsOffsets) {
            forUtil.decode(payIn, offsetStartDeltaBuffer);
            forUtil.decode(payIn, offsetLengthBuffer);
          } else {
            // this works, because when writing a vint block we always force the first length to be written
            forUtil.skip(payIn); // skip over starts
            forUtil.skip(payIn); // skip over lengths
          }
        }
      }
    }

    @Override
    public void advanceShallow(int target) throws IOException {
      if (target > nextSkipDoc) {
        // always plus one to fix the result, since skip position in Lucene84SkipReader 
        // is a little different from MultiLevelSkipListReader
        final int newDocUpto = skipper.skipTo(target) + 1; 
  
        if (newDocUpto > docUpto) {
          // Skipper moved
          assert newDocUpto % BLOCK_SIZE == 0 : "got " + newDocUpto;
          docUpto = newDocUpto;
          posDocUpTo = docUpto;

          // Force to read next block
          docBufferUpto = BLOCK_SIZE;
          accum = skipper.getDoc();
          posPendingFP = skipper.getPosPointer();
          payPendingFP = skipper.getPayPointer();
          posPendingCount = skipper.getPosBufferUpto();
          lastStartOffset = 0; // new document
          payloadByteUpto = skipper.getPayloadByteUpto();             // actually, this is just lastSkipEntry
          seekTo = skipper.getDocPointer();       // delay the seek
        }
        // next time we call advance, this is used to 
        // foresee whether skipper is necessary.
        nextSkipDoc = skipper.getNextSkipDoc();
      }
      assert nextSkipDoc >= target;
    }

    @Override
    public Impacts getImpacts() throws IOException {
      advanceShallow(doc);
      return skipper.getImpacts();
    }

    @Override
    public int nextDoc() throws IOException {
      return advance(doc + 1);
    }

    @Override
    public int advance(int target) throws IOException {
      if (target > nextSkipDoc) {
        advanceShallow(target);
      }
      if (docUpto == docFreq) {
        return doc = NO_MORE_DOCS;
      }
      if (docBufferUpto == BLOCK_SIZE) {
        if (seekTo >= 0) {
          docIn.seek(seekTo);
          seekTo = -1;
          isFreqsRead = true; // reset isFreqsRead
        }
        refillDocs();
      }

      // Now scan:
      while (true) {
        accum += docDeltaBuffer.get(docBufferUpto);
        docBufferUpto++;
        docUpto++;

        if (accum >= target) {
          break;
        }
        if (docUpto == docFreq) {
          return doc = NO_MORE_DOCS;
        }
      }
      position = 0;
      lastStartOffset = 0;

      return doc = accum;
    }

    // TODO: in theory we could avoid loading frq block
    // when not needed, ie, use skip data to load how far to
    // seek the pos pointer ... instead of having to load frq
    // blocks only to sum up how many positions to skip
    private void skipPositions() throws IOException {
      // Skip positions now:
      int toSkip = posPendingCount - freqBuffer.get(docBufferUpto-1);
      // if (DEBUG) {
      //   System.out.println("      FPR.skipPositions: toSkip=" + toSkip);
      // }

      final int leftInBlock = BLOCK_SIZE - posBufferUpto;
      if (toSkip < leftInBlock) {
        int end = posBufferUpto + toSkip;
        while(posBufferUpto < end) {
          if (indexHasPayloads) {
            payloadByteUpto += payloadLengthBuffer.get(posBufferUpto);;
          }
          posBufferUpto++;
        }
      } else {
        toSkip -= leftInBlock;
        while(toSkip >= BLOCK_SIZE) {
          assert posIn.getFilePointer() != lastPosBlockFP;
          forUtil.skip(posIn);
  
          if (indexHasPayloads && payIn != null) {
            // Skip payloadLength block:
            forUtil.skip(payIn);

            // Skip payloadBytes block:
            int numBytes = payIn.readVInt();
            payIn.seek(payIn.getFilePointer() + numBytes);
          }

          if (indexHasOffsets && payIn != null) {
            forUtil.skip(payIn);
            forUtil.skip(payIn);
          }
          toSkip -= BLOCK_SIZE;
        }
        refillPositions();
        payloadByteUpto = 0;
        posBufferUpto = 0;
        while(posBufferUpto < toSkip) {
          if (indexHasPayloads) {
            payloadByteUpto += payloadLengthBuffer.get(posBufferUpto);;
          }
          posBufferUpto++;
        }
      }

      position = 0;
      lastStartOffset = 0;
    }

    @Override
    public int nextPosition() throws IOException {
      if (indexHasPos == false || needsPositions == false) {
        return -1;
      }

      if (isFreqsRead == false) {
        forUtil.decode(docIn, freqBuffer); // read freqBuffer for this docs block
        isFreqsRead = true;
      }
      while (posDocUpTo < docUpto) { // bring posPendingCount upto the current doc
        posPendingCount += freqBuffer.get(docBufferUpto - (docUpto - posDocUpTo));
        posDocUpTo++;
      }

      assert posPendingCount > 0;
      
      if (posPendingFP != -1) {
        posIn.seek(posPendingFP);
        posPendingFP = -1;

        if (payPendingFP != -1 && payIn != null) {
          payIn.seek(payPendingFP);
          payPendingFP = -1;
        }

        // Force buffer refill:
        posBufferUpto = BLOCK_SIZE;
      }

      if (posPendingCount > freqBuffer.get(docBufferUpto-1)) {
        skipPositions();
        posPendingCount = freqBuffer.get(docBufferUpto-1);
      }

      if (posBufferUpto == BLOCK_SIZE) {
        refillPositions();
        posBufferUpto = 0;
      }
      position += posDeltaBuffer.get(posBufferUpto);;

      if (indexHasPayloads) {
        payloadLength = payloadLengthBuffer.get(posBufferUpto);;
        payload.bytes = payloadBytes;
        payload.offset = payloadByteUpto;
        payload.length = payloadLength;
        payloadByteUpto += payloadLength;
      }

      if (indexHasOffsets && needsOffsets) {
        startOffset = lastStartOffset + offsetStartDeltaBuffer.get(posBufferUpto);;
        endOffset = startOffset + offsetLengthBuffer.get(posBufferUpto);;
        lastStartOffset = startOffset;
      }

      posBufferUpto++;
      posPendingCount--;
      return position;
    }

    @Override
    public int startOffset() {
      return startOffset;
    }
  
    @Override
    public int endOffset() {
      return endOffset;
    }
  
    @Override
    public BytesRef getPayload() {
      if (payloadLength == 0) {
        return null;
      } else {
        return payload;
      }
    }
    
    @Override
    public long cost() {
      return docFreq;
    }

  }

  @Override
  public long ramBytesUsed() {
    return BASE_RAM_BYTES_USED;
  }

  @Override
  public void checkIntegrity() throws IOException {
    if (docIn != null) {
      CodecUtil.checksumEntireFile(docIn);
    }
    if (posIn != null) {
      CodecUtil.checksumEntireFile(posIn);
    }
    if (payIn != null) {
      CodecUtil.checksumEntireFile(payIn);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(positions=" + (posIn != null) + ",payloads=" + (payIn != null) +")";
  }
}
