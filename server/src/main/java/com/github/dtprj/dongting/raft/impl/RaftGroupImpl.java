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
package com.github.dtprj.dongting.raft.impl;

import com.github.dtprj.dongting.common.DtTime;
import com.github.dtprj.dongting.common.FlowControlException;
import com.github.dtprj.dongting.common.Timestamp;
import com.github.dtprj.dongting.fiber.FiberFrame;
import com.github.dtprj.dongting.fiber.FiberFuture;
import com.github.dtprj.dongting.fiber.FiberGroup;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import com.github.dtprj.dongting.raft.RaftException;
import com.github.dtprj.dongting.raft.server.LogItem;
import com.github.dtprj.dongting.raft.server.NotLeaderException;
import com.github.dtprj.dongting.raft.server.RaftCallback;
import com.github.dtprj.dongting.raft.server.RaftGroup;
import com.github.dtprj.dongting.raft.server.RaftGroupConfigEx;
import com.github.dtprj.dongting.raft.server.RaftInput;
import com.github.dtprj.dongting.raft.sm.StateMachine;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author huangli
 */
public class RaftGroupImpl extends RaftGroup {
    private static final DtLog log = DtLogs.getLogger(RaftGroupImpl.class);
    private final int groupId;
    private final RaftStatusImpl raftStatus;
    private final RaftGroupConfigEx groupConfig;
    private final PendingStat serverStat;
    private final StateMachine stateMachine;
    private final FiberGroup fiberGroup;

    private final Timestamp readTimestamp = new Timestamp();
    private final GroupComponents gc;
    private CompletableFuture<Void> shutdownFuture;

    public RaftGroupImpl(GroupComponents gc) {
        this.gc = gc;
        this.groupId = gc.getGroupConfig().getGroupId();

        this.raftStatus = gc.getRaftStatus();
        this.groupConfig = gc.getGroupConfig();
        this.serverStat = gc.getServerStat();
        this.stateMachine = gc.getStateMachine();
        this.fiberGroup = gc.getFiberGroup();
    }

    @Override
    public int getGroupId() {
        return groupId;
    }

    @Override
    public boolean isLeader() {
        RaftMember leader = raftStatus.getShareStatus().currentLeader;
        return leader != null && leader.getNode().isSelf();
    }

    @Override
    public StateMachine getStateMachine() {
        return stateMachine;
    }

    @Override
    public void submitLinearTask(RaftInput input, RaftCallback callback) {
        Objects.requireNonNull(input);
        if (fiberGroup.isShouldStop()) {
            RaftUtil.release(input);
            throw new RaftException("raft group thread is stop");
        }
        int currentPendingWrites = (int) PendingStat.PENDING_REQUESTS.getAndAddRelease(serverStat, 1);
        if (currentPendingWrites >= groupConfig.getMaxPendingRaftTasks()) {
            RaftUtil.release(input);
            String msg = "submitRaftTask failed: too many pending writes, currentPendingWrites=" + currentPendingWrites;
            log.warn(msg);
            PendingStat.PENDING_REQUESTS.getAndAddRelease(serverStat, -1);
            throw new FlowControlException(msg);
        }
        long size = input.getFlowControlSize();
        long currentPendingWriteBytes = (long) PendingStat.PENDING_BYTES.getAndAddRelease(serverStat, size);
        if (currentPendingWriteBytes >= groupConfig.getMaxPendingTaskBytes()) {
            RaftUtil.release(input);
            String msg = "too many pending write bytes,currentPendingWriteBytes="
                    + currentPendingWriteBytes + ", currentRequestBytes=" + size;
            log.warn(msg);
            PendingStat.PENDING_BYTES.getAndAddRelease(serverStat, -size);
            throw new FlowControlException(msg);
        }
        RaftCallback wrapper = new RaftCallback() {
            @Override
            public void success(long raftIndex, Object result) {
                PendingStat.PENDING_REQUESTS.getAndAddRelease(serverStat, -1);
                PendingStat.PENDING_BYTES.getAndAddRelease(serverStat, -size);
                RaftCallback.callSuccess(callback, raftIndex, result);
            }

            @Override
            public void fail(Throwable ex) {
                PendingStat.PENDING_REQUESTS.getAndAddRelease(serverStat, -1);
                PendingStat.PENDING_BYTES.getAndAddRelease(serverStat, -size);
                RaftCallback.callFail(callback, ex);
            }
        };
        int type = input.isReadOnly() ? LogItem.TYPE_LOG_READ : LogItem.TYPE_NORMAL;
        gc.getLinearTaskRunner().submitRaftTaskInBizThread(type, input, wrapper);
    }

    @Override
    public CompletableFuture<Long> getLeaseReadIndex(DtTime deadline) {
        if (fiberGroup.isShouldStop()) {
            return CompletableFuture.failedFuture(new RaftException("raft group thread is stop"));
        }
        ShareStatus ss = raftStatus.getShareStatus();
        if (ss.role != RaftRole.leader) {
            return CompletableFuture.failedFuture(new NotLeaderException(
                    ss.currentLeader == null ? null : ss.currentLeader.getNode()));
        }

        // NOTICE : timestamp is not thread safe
        if (ss.groupReady) {
            readTimestamp.refresh(1);
            long t = readTimestamp.getNanoTime();
            if (ss.leaseEndNanos - t < 0) {
                return CompletableFuture.failedFuture(new NotLeaderException(null));
            }
            return CompletableFuture.completedFuture(ss.lastApplied);
        }

        // wait group ready
        return gc.getApplyManager().addToWaitReadyQueue(deadline);
    }

    @Override
    public void markTruncateByIndex(long index, long delayMillis) {
        ExecutorService executor = gc.getFiberGroup().getExecutor();
        executor.execute(() -> gc.getRaftLog().markTruncateByIndex(index, delayMillis));
    }

    @Override
    public void markTruncateByTimestamp(long timestampMillis, long delayMillis) {
        ExecutorService executor = gc.getFiberGroup().getExecutor();
        executor.execute(() -> gc.getRaftLog().markTruncateByTimestamp(timestampMillis, delayMillis));
    }

    @Override
    public CompletableFuture<Long> fireSaveSnapshot() {
        checkStatus();
        CompletableFuture<Long> f = new CompletableFuture<>();
        gc.getFiberGroup().getExecutor().execute(() -> {
            try {
                FiberFuture<Long> ff = gc.getSnapshotManager().saveSnapshot();
                ff.registerCallback((idx, ex) -> {
                    if (ex != null) {
                        f.completeExceptionally(ex);
                    } else {
                        f.complete(idx);
                    }
                });
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    @Override
    public CompletableFuture<Void> transferLeadership(int nodeId, long timeoutMillis) {
        checkStatus();
        CompletableFuture<Void> f = new CompletableFuture<>();
        DtTime deadline = new DtTime(timeoutMillis, TimeUnit.MILLISECONDS);
        gc.getMemberManager().transferLeadership(nodeId, f, deadline);
        return f;
    }

    private void checkStatus() {
        CompletableFuture<Void> f = gc.getMemberManager().getPingReadyFuture();
        if (!f.isDone() || f.isCompletedExceptionally()) {
            throw new RaftException("not initialized");
        }
        if (raftStatus.getFiberGroup().isShouldStop()) {
            throw new RaftException("raft group is not running");
        }
    }

    @Override
    public CompletableFuture<Long> leaderPrepareJointConsensus(Set<Integer> members, Set<Integer> observers) {
        Objects.requireNonNull(members);
        Objects.requireNonNull(observers);
        if (members.isEmpty()) {
            throw new RaftException("members are empty");
        }
        checkStatus();
        for (int nodeId : members) {
            if (observers.contains(nodeId)) {
                log.error("node is both member and observer: nodeId={}, groupId={}", nodeId, groupId);
                throw new RaftException("node is both member and observer: " + nodeId);
            }
        }
        CompletableFuture<Long> f = new CompletableFuture<>();
        FiberFrame<Void> ff = gc.getMemberManager().leaderPrepareJointConsensus(members, observers, f);
        gc.getFiberGroup().fireFiber("leaderPrepareJointConsensus", ff);
        return f;
    }

    @Override
    public CompletableFuture<Long> leaderAbortJointConsensus() {
        checkStatus();
        CompletableFuture<Long> f = new CompletableFuture<>();
        FiberFrame<Void> ff = gc.getMemberManager().leaderAbortJointConsensus(f);
        gc.getFiberGroup().fireFiber("leaderAbortJointConsensus", ff);
        return f;
    }

    @Override
    public CompletableFuture<Long> leaderCommitJointConsensus(long prepareIndex) {
        checkStatus();
        CompletableFuture<Long> f = new CompletableFuture<>();
        FiberFrame<Void> ff = gc.getMemberManager().leaderCommitJointConsensus(f, prepareIndex);
        gc.getFiberGroup().fireFiber("leaderCommitJointConsensus", ff);
        return f;
    }

    public GroupComponents getGroupComponents() {
        return gc;
    }

    public FiberGroup getFiberGroup() {
        return fiberGroup;
    }

    public CompletableFuture<Void> getShutdownFuture() {
        return shutdownFuture;
    }

    public void setShutdownFuture(CompletableFuture<Void> shutdownFuture) {
        this.shutdownFuture = shutdownFuture;
    }
}
