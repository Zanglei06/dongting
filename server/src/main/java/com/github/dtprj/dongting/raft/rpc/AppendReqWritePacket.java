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
package com.github.dtprj.dongting.raft.rpc;

import com.github.dtprj.dongting.codec.Encodable;
import com.github.dtprj.dongting.codec.EncodeContext;
import com.github.dtprj.dongting.codec.PbUtil;
import com.github.dtprj.dongting.net.WritePacket;
import com.github.dtprj.dongting.raft.impl.RaftUtil;
import com.github.dtprj.dongting.raft.server.LogItem;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author huangli
 */
//message AppendEntriesReq {
//  uint32 group_id = 1;
//  uint32 term = 2;
//  uint32 leader_id = 3;
//  fixed64 prev_log_index = 4;
//  uint32 prev_log_term = 5;
//  fixed64 leader_commit = 6;
//  repeated LogItem entries = 7;
//}
//
//message LogItem {
//  uint32 type = 1;
//  uint32 bizType = 2;
//  uint32 term = 3;
//  fixed64 index = 4;
//  uint32 prev_log_term = 5;
//  fixed64 timestamp = 6;
//  bytes header = 7;
//  bytes body = 8;
//}
public class AppendReqWritePacket extends WritePacket {

    int groupId;
    int term;
    int leaderId;
    long prevLogIndex;
    int prevLogTerm;
    long leaderCommit;
    List<LogItem> logs;

    private int headerSize;

    private static final int WRITE_HEADER = 0;
    private static final int WRITE_ITEM_HEADER = 1;
    private static final int WRITE_ITEM_BIZ_HEADER_LEN = 2;
    private static final int WRITE_ITEM_BIZ_HEADER = 3;
    private static final int WRITE_ITEM_BIZ_BODY_LEN = 4;
    private static final int WRITE_ITEM_BIZ_BODY = 5;
    private int writeStatus;
    private int encodeLogIndex;

    private LogItem currentItem;

    public AppendReqWritePacket() {
    }

    @Override
    protected int calcActualBodySize() {
        headerSize = PbUtil.accurateUnsignedIntSize(1, groupId)
                + PbUtil.accurateUnsignedIntSize(2, term)
                + PbUtil.accurateUnsignedIntSize(3, leaderId)
                + PbUtil.accurateFix64Size(4, prevLogIndex)
                + PbUtil.accurateUnsignedIntSize(5, prevLogTerm)
                + PbUtil.accurateFix64Size(6, leaderCommit);
        int x = headerSize;
        for (LogItem item : logs) {
            int itemSize = computeItemSize(item);
            x += PbUtil.accurateLengthDelimitedSize(7, itemSize);
        }
        return x;
    }

    private int computeItemSize(LogItem item) {
        int itemSize = item.getPbItemSize();
        if (itemSize > 0) {
            return itemSize;
        }
        int itemHeaderSize = PbUtil.accurateUnsignedIntSize(1, item.getType())
                + PbUtil.accurateUnsignedIntSize(2, item.getBizType())
                + PbUtil.accurateUnsignedIntSize(3, item.getTerm())
                + PbUtil.accurateFix64Size(4, item.getIndex())
                + PbUtil.accurateUnsignedIntSize(5, item.getPrevLogTerm())
                + PbUtil.accurateFix64Size(6, item.getTimestamp());
        itemSize = itemHeaderSize
                + PbUtil.accurateLengthDelimitedSize(7, item.getActualHeaderSize())
                + PbUtil.accurateLengthDelimitedSize(8, item.getActualBodySize());
        item.setPbItemSize(itemSize);
        item.setPbHeaderSize(itemHeaderSize);
        return itemSize;
    }

    @Override
    protected boolean encodeBody(EncodeContext context, ByteBuffer dest) {
        while (true) {
            switch (writeStatus) {
                case WRITE_HEADER:
                    if (dest.remaining() < headerSize) {
                        return false;
                    }
                    PbUtil.writeUnsignedInt32(dest, 1, groupId);
                    PbUtil.writeUnsignedInt32(dest, 2, term);
                    PbUtil.writeUnsignedInt32(dest, 3, leaderId);
                    PbUtil.writeFix64(dest, 4, prevLogIndex);
                    PbUtil.writeUnsignedInt32(dest, 5, prevLogTerm);
                    PbUtil.writeFix64(dest, 6, leaderCommit);
                    writeStatus = WRITE_ITEM_HEADER;
                    break;
                case WRITE_ITEM_HEADER:
                    if (encodeLogIndex < logs.size()) {
                        currentItem = logs.get(encodeLogIndex);
                    } else {
                        return true;
                    }
                    if (dest.remaining() < PbUtil.accurateLengthDelimitedPrefixSize(
                            7, computeItemSize(currentItem)) + currentItem.getPbHeaderSize()) {
                        return false;
                    }
                    PbUtil.writeLengthDelimitedPrefix(dest, 7, computeItemSize(currentItem));

                    PbUtil.writeUnsignedInt32(dest, 1, currentItem.getType());
                    PbUtil.writeUnsignedInt32(dest, 2, currentItem.getBizType());
                    PbUtil.writeUnsignedInt32(dest, 3, currentItem.getTerm());
                    PbUtil.writeFix64(dest, 4, currentItem.getIndex());
                    PbUtil.writeUnsignedInt32(dest, 5, currentItem.getPrevLogTerm());
                    PbUtil.writeFix64(dest, 6, currentItem.getTimestamp());
                    writeStatus = WRITE_ITEM_BIZ_HEADER_LEN;
                    break;
                case WRITE_ITEM_BIZ_HEADER_LEN:
                    if (currentItem.getActualHeaderSize() <= 0) {
                        writeStatus = WRITE_ITEM_BIZ_BODY_LEN;
                        break;
                    }
                    if (dest.remaining() < PbUtil.accurateLengthDelimitedPrefixSize(
                            7, currentItem.getActualHeaderSize())) {
                        return false;
                    }
                    PbUtil.writeLengthDelimitedPrefix(dest, 7, currentItem.getActualHeaderSize());
                    writeStatus = WRITE_ITEM_BIZ_HEADER;
                    break;
                case WRITE_ITEM_BIZ_HEADER:
                    if (!writeData(context, dest, currentItem.getHeader())) {
                        return false;
                    }
                    writeStatus = WRITE_ITEM_BIZ_BODY_LEN;
                    break;
                case WRITE_ITEM_BIZ_BODY_LEN:
                    if (currentItem.getActualBodySize() <= 0) {
                        currentItem = null;
                        encodeLogIndex++;
                        writeStatus = WRITE_ITEM_HEADER;
                        break;
                    }
                    if (dest.remaining() < PbUtil.accurateLengthDelimitedPrefixSize(
                            8, currentItem.getActualBodySize())) {
                        return false;
                    }
                    PbUtil.writeLengthDelimitedPrefix(dest, 8, currentItem.getActualBodySize());
                    writeStatus = WRITE_ITEM_BIZ_BODY;
                    break;
                case WRITE_ITEM_BIZ_BODY:
                    if (!writeData(context, dest, currentItem.getBody())) {
                        return false;
                    }
                    currentItem = null;
                    encodeLogIndex++;
                    writeStatus = WRITE_ITEM_HEADER;
                    break;
                default:
                    throw new IllegalStateException("unknown write status " + writeStatus);
            }
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean writeData(EncodeContext context, ByteBuffer dest, Encodable data) {
        if (!dest.hasRemaining()) {
            return false;
        }
        boolean result = false;
        try {
            result = data.encode(context, dest);
            return result;
        } catch (RuntimeException | Error e){
            context.reset();
            throw e;
        } finally {
            if (result) {
                context.reset();
            }
        }
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public void setLeaderId(int leaderId) {
        this.leaderId = leaderId;
    }

    public void setPrevLogIndex(long prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public void setLeaderCommit(long leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    public void setLogs(List<LogItem> logs) {
        this.logs = logs;
    }

    @Override
    protected void doClean() {
        RaftUtil.release(logs);
    }
}
