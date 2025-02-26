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

import com.github.dtprj.dongting.codec.PbCallback;
import com.github.dtprj.dongting.codec.PbUtil;
import com.github.dtprj.dongting.codec.SimpleEncodable;
import com.github.dtprj.dongting.raft.RaftRpcData;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
//  uint32 term = 1;
//  uint32 vote_granted = 2;
public class VoteResp extends RaftRpcData implements SimpleEncodable {
    // public int term;
    public boolean voteGranted;

    @Override
    public int actualSize() {
        return PbUtil.sizeOfInt32Field(1, term)
                + PbUtil.sizeOfInt32Field(2, voteGranted ? 1 : 0);
    }

    @Override
    public void encode(ByteBuffer buf) {
        PbUtil.writeInt32Field(buf, 1, term);
        PbUtil.writeInt32Field(buf, 2, voteGranted ? 1 : 0);
    }

    public static class Callback extends PbCallback<VoteResp> {
        private final VoteResp result = new VoteResp();

        @Override
        public boolean readVarNumber(int index, long value) {
            switch (index) {
                case 1:
                    result.term = (int) value;
                    break;
                case 2:
                    result.voteGranted = value != 0;
                    break;
            }
            return true;
        }

        @Override
        public VoteResp getResult() {
            return result;
        }
    }
}
