/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hdgf.streams;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.hdgf.HDGFLZW;
import org.apache.poi.util.IOUtils;

/**
 * A StreamStore where the data on-disk is compressed,
 *  using the crazy Visio LZW
 */
public final class CompressedStreamStore extends StreamStore {

    //arbitrarily selected; may need to increase
    private static final int DEFAULT_MAX_RECORD_LENGTH = 64_000_000;
    private static int MAX_RECORD_LENGTH = DEFAULT_MAX_RECORD_LENGTH;

    /** The raw, compressed contents */
    private byte[] compressedContents;
    /**
     * We're not sure what this is, but it comes before the
     *  real contents in the de-compressed data
     */
    private final byte[] blockHeader;
    private boolean blockHeaderInContents;

    byte[] _getCompressedContents() { return compressedContents; }
    byte[] _getBlockHeader() { return blockHeader; }

    /**
     * @param length the max record length allowed for CompressedStreamStore
     */
    public static void setMaxRecordLength(int length) {
        MAX_RECORD_LENGTH = length;
    }

    /**
     * @return the max record length allowed for CompressedStreamStore
     */
    public static int getMaxRecordLength() {
        return MAX_RECORD_LENGTH;
    }

    /**
     * Creates a new compressed StreamStore, which will handle
     *  the decompression.
     */
    CompressedStreamStore(byte[] data, int offset, int length) throws IOException {
        this(decompress(data,offset,length));
        compressedContents = IOUtils.safelyClone(data, offset, length, MAX_RECORD_LENGTH);
    }
    /**
     * Handles passing the de-compressed data onto our superclass.
     */
    private CompressedStreamStore(byte[][] decompressedData) {
        super(decompressedData[1], 0, decompressedData[1].length);
        blockHeader = decompressedData[0];
    }

    /**
     * Some kinds of streams expect their 4 byte header to be
     *  on the front of the contents.
     * They can call this to have it sorted.
     */
    protected void copyBlockHeaderToContents() {
        if(blockHeaderInContents) return;

        prependContentsWith(blockHeader);
        blockHeaderInContents = true;
    }


    /**
     * Decompresses the given data, returning it as header + contents
     */
    public static byte[][] decompress(byte[] data, int offset, int length) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data, offset, length);

        // Decompress
        HDGFLZW lzw = new HDGFLZW();
        byte[] decompressed = lzw.decompress(bais);

        if (decompressed.length < 4) {
            throw new IllegalArgumentException("Could not read enough data to decompress: " + decompressed.length);
        }

        // Split into header and contents
        byte[][] ret = new byte[2][];
        ret[0] = new byte[4];
        ret[1] = new byte[decompressed.length - 4];

        System.arraycopy(decompressed, 0, ret[0], 0, 4);
        System.arraycopy(decompressed, 4, ret[1], 0, ret[1].length);

        // All done
        return ret;
    }
}
