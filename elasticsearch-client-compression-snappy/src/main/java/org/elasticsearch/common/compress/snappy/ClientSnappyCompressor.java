/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.compress.snappy;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.BasicCompressor;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BasicCachedStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;

import java.io.IOException;

/**
 */
public abstract class ClientSnappyCompressor implements BasicCompressor {

    public static final byte[] HEADER = {'s', 'n', 'a', 'p', 'p', 'y', 0};

    protected SnappyCompressorContext compressorContext;

    // default block size (32k)
    static final int DEFAULT_CHUNK_SIZE = 1 << 15;

    protected ClientSnappyCompressor() {
        this.compressorContext = new SnappyCompressorContext(DEFAULT_CHUNK_SIZE, maxCompressedLength(DEFAULT_CHUNK_SIZE));
    }

    @Override
    public void configure(Settings settings) {
        int chunkLength = (int) settings.getAsBytesSize("compress.snappy.chunk_size", new ByteSizeValue(compressorContext.compressChunkLength())).bytes();
        int maxCompressedChunkLength = maxCompressedLength(chunkLength);
        this.compressorContext = new SnappyCompressorContext(chunkLength, maxCompressedChunkLength);
    }

    protected abstract int maxCompressedLength(int length);

    @Override
    public boolean isCompressed(byte[] data, int offset, int length) {
        if (length < HEADER.length) {
            return false;
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (data[offset + i] != HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompressed(BytesReference bytes) {
        if (bytes.length() < HEADER.length) {
            return false;
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (bytes.get(i) != HEADER[i]) {
                return false;
            }
        }
        return true;
    }


    @Override
    public byte[] compress(byte[] data, int offset, int length) throws IOException {
        // this needs to be the same format as regular streams reading from it!
        BasicCachedStreamOutput.Entry entry = BasicCachedStreamOutput.popEntry();
        try {
            StreamOutput compressed = entry.bytes(this);
            compressed.writeBytes(data, offset, length);
            compressed.close();
            return entry.bytes().bytes().copyBytesArray().toBytes();
        } finally {
            BasicCachedStreamOutput.pushEntry(entry);
        }
    }

    @Override
    public byte[] uncompress(byte[] data, int offset, int length) throws IOException {
        StreamInput compressed = streamInput(new BytesStreamInput(data, offset, length, false));
        BasicCachedStreamOutput.Entry entry = BasicCachedStreamOutput.popEntry();
        try {
            Streams.copy(compressed, entry.bytes());
            return entry.bytes().bytes().copyBytesArray().toBytes();
        } finally {
            BasicCachedStreamOutput.pushEntry(entry);
        }
    }
}
