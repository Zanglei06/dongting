/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.dtkv;

import com.github.dtprj.dongting.codec.CodecException;
import com.github.dtprj.dongting.codec.Encodable;
import com.github.dtprj.dongting.codec.EncodeContext;
import com.github.dtprj.dongting.codec.EncodeUtil;
import com.github.dtprj.dongting.codec.PbUtil;
import com.github.dtprj.dongting.common.ByteArray;
import com.github.dtprj.dongting.raft.RaftRpcData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author huangli
 */
public class KvReq extends RaftRpcData implements Encodable {
    private static final int IDX_GROUP_ID = 1;
    private static final int IDX_KEY = 2;
    private static final int IDX_VALUE = 3;
    private static final int IDX_KEYS_SIZE = 4;
    private static final int IDX_KEYS = 5;
    private static final int IDX_VALUES_SIZE = 6;
    private static final int IDX_VALUES = 7;
    private static final int IDX_EXPECT_VALUE = 8;

    private final byte[] key;
    private final ByteArray value;
    private final ArrayList<byte[]> keys;
    private final ArrayList<? extends ByteArray> values;
    private final ByteArray expectValue;

    private int size;

    public KvReq(int groupId, byte[] key, ByteArray value, ArrayList<byte[]> keys,
                 ArrayList<? extends ByteArray> values, ByteArray expectValue) {
        this.groupId = groupId;
        this.key = key;
        this.value = value;
        this.keys = keys;
        this.values = values;
        this.expectValue = expectValue;
    }

    @Override
    public int actualSize() {
        if (size == 0) {
            size = PbUtil.accurateUnsignedIntSize(IDX_GROUP_ID, groupId)
                    + EncodeUtil.actualSize(IDX_KEY, key)
                    + EncodeUtil.actualSize(IDX_VALUE, value)
                    + PbUtil.accurateUnsignedIntSize(IDX_KEYS_SIZE, keys == null ? 0 : keys.size())
                    + EncodeUtil.actualSizeOfBytes(IDX_KEYS, keys)
                    + PbUtil.accurateUnsignedIntSize(IDX_VALUES_SIZE, values == null ? 0 : values.size())
                    + EncodeUtil.actualSizeOfObjs(IDX_VALUES, values)
                    + EncodeUtil.actualSize(IDX_EXPECT_VALUE, expectValue);
        }
        return size;
    }

    @Override
    public boolean encode(EncodeContext context, ByteBuffer destBuffer) {
        if (context.stage == EncodeContext.STAGE_BEGIN) {
            if (groupId != 0) {
                if (destBuffer.remaining() < PbUtil.maxUnsignedIntSize()) {
                    return false;
                }
                PbUtil.writeUnsignedInt32(destBuffer, IDX_GROUP_ID, groupId);
            }
            context.stage = IDX_GROUP_ID;
        }
        if (context.stage == IDX_GROUP_ID) {
            if (key != null && !EncodeUtil.encode(context, destBuffer, IDX_KEY, key)) {
                return false;
            }
            context.stage = IDX_KEY;
        }
        if (context.stage == IDX_KEY) {
            if (value != null && !EncodeUtil.encode(context, destBuffer, IDX_VALUE, value)) {
                return false;
            } else {
                context.stage = IDX_VALUE;
            }
        }
        if (context.stage == IDX_VALUE) {
            if (keys != null) {
                if (destBuffer.remaining() < PbUtil.maxUnsignedIntSize()) {
                    return false;
                }
                PbUtil.writeUnsignedInt32(destBuffer, IDX_KEYS_SIZE, keys.size());
            }
            context.stage = IDX_KEYS_SIZE;
        }
        if (context.stage == IDX_KEYS_SIZE) {
            if (keys != null && !EncodeUtil.encodeBytes(context, destBuffer, IDX_KEYS, keys)) {
                return false;
            } else {
                context.stage = IDX_KEYS;
            }
        }
        if (context.stage == IDX_KEYS) {
            if (values != null) {
                if (destBuffer.remaining() < PbUtil.maxUnsignedIntSize()) {
                    return false;
                }
                PbUtil.writeUnsignedInt32(destBuffer, IDX_VALUES_SIZE, values.size());
            }
            context.stage = IDX_VALUES_SIZE;
        }
        if (context.stage == IDX_VALUES_SIZE) {
            if (values != null && !EncodeUtil.encodeObjs(context, destBuffer, IDX_VALUES, values)) {
                return false;
            } else {
                context.stage = IDX_VALUES;
            }
        }
        if (context.stage == IDX_VALUES) {
            if (expectValue != null && !EncodeUtil.encode(context, destBuffer, IDX_EXPECT_VALUE, expectValue)) {
                return false;
            } else {
                context.stage = EncodeContext.STAGE_END;
                return true;
            }
        }
        throw new CodecException(context);
    }

    public byte[] getKey() {
        return key;
    }

    public ByteArray getValue() {
        return value;
    }

    public List<byte[]> getKeys() {
        return keys;
    }

    public List<? extends ByteArray> getValues() {
        return values;
    }

    public ByteArray getExpectValue() {
        return expectValue;
    }
}
