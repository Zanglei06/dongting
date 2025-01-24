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

import com.github.dtprj.dongting.codec.DecodeContext;
import com.github.dtprj.dongting.codec.DecoderCallback;
import com.github.dtprj.dongting.codec.PbIntCallback;
import com.github.dtprj.dongting.fiber.FiberFrame;
import com.github.dtprj.dongting.net.CmdCodes;
import com.github.dtprj.dongting.net.ReadPacket;
import com.github.dtprj.dongting.net.SimpleWritePacket;
import com.github.dtprj.dongting.raft.QueryStatusResp;
import com.github.dtprj.dongting.raft.impl.RaftStatusImpl;
import com.github.dtprj.dongting.raft.server.RaftServer;

/**
 * @author huangli
 */
public class QueryStatusProcessor extends RaftSequenceProcessor<Integer> {

    public QueryStatusProcessor(RaftServer raftServer) {
        super(raftServer);
    }

    @Override
    public DecoderCallback<Integer> createDecoderCallback(int command, DecodeContext context) {
        return context.toDecoderCallback(new PbIntCallback());
    }

    @Override
    protected int getGroupId(ReadPacket<Integer> frame) {
        Integer x = frame.getBody();
        return x == null ? 0 : x;
    }

    @Override
    protected FiberFrame<Void> processInFiberGroup(ReqInfoEx<Integer> reqInfo) {
        RaftStatusImpl raftStatus = reqInfo.getRaftGroup().getGroupComponents().getRaftStatus();
        QueryStatusResp resp = new QueryStatusResp();
        resp.setGroupId(reqInfo.getRaftGroup().getGroupId());
        resp.setLeaderId(raftStatus.getCurrentLeader() == null ? 0 : raftStatus.getCurrentLeader().getNode().getNodeId());
        resp.setTerm(raftStatus.getCurrentTerm());
        resp.setCommitIndex(raftStatus.getCommitIndex());
        resp.setLastApplied(raftStatus.getLastApplied());
        resp.setLastLogIndex(raftStatus.getLastLogIndex());
        resp.setMembers(raftStatus.getNodeIdOfMembers());
        resp.setObservers(raftStatus.getNodeIdOfObservers());
        resp.setPreparedMembers(raftStatus.getNodeIdOfPreparedMembers());
        resp.setPreparedObservers(raftStatus.getNodeIdOfPreparedObservers());

        SimpleWritePacket wf = new SimpleWritePacket(resp);
        wf.setRespCode(CmdCodes.SUCCESS);
        writeResp(reqInfo, wf);
        return FiberFrame.voidCompletedFrame();
    }
}
