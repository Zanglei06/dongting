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
package com.github.dtprj.dongting.net;

import com.github.dtprj.dongting.buf.DefaultPoolFactory;
import com.github.dtprj.dongting.buf.PoolFactory;
import com.github.dtprj.dongting.codec.DecodeContext;
import com.github.dtprj.dongting.common.ConfigBase;
import com.github.dtprj.dongting.common.NoopPerfCallback;
import com.github.dtprj.dongting.common.PerfCallback;

import java.util.function.Supplier;

/**
 * @author huangli
 */
public abstract class NioConfig extends ConfigBase {

    private int bizThreads;
    private String name;

    // back pressure config
    private int maxOutRequests;
    private long maxOutBytes;
    private int maxInRequests;
    private long maxInBytes;

    private long selectTimeout = 50;
    private long cleanInterval = 100;

    private int maxPacketSize = 5 * 1024 * 1024;
    private int maxBodySize = 4 * 1024 * 1024;

    private PoolFactory poolFactory = new DefaultPoolFactory();

    private int readBufferSize = 128 * 1024;

    private boolean finishPendingImmediatelyWhenChannelClose = false;

    private PerfCallback perfCallback = NoopPerfCallback.INSTANCE;
    private Supplier<DecodeContext> decodeContextFactory = DecodeContext::new;

    private ChannelListener channelListener;

    private boolean serverHint = true;

    public int getBizThreads() {
        return bizThreads;
    }

    public void setBizThreads(int bizThreads) {
        this.bizThreads = bizThreads;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxOutRequests() {
        return maxOutRequests;
    }

    public void setMaxOutRequests(int maxOutRequests) {
        this.maxOutRequests = maxOutRequests;
    }

    public long getSelectTimeout() {
        return selectTimeout;
    }

    public void setSelectTimeout(long selectTimeout) {
        this.selectTimeout = selectTimeout;
    }

    public long getCleanInterval() {
        return cleanInterval;
    }

    public void setCleanInterval(long cleanInterval) {
        this.cleanInterval = cleanInterval;
    }

    public int getMaxInRequests() {
        return maxInRequests;
    }

    public void setMaxInRequests(int maxInRequests) {
        this.maxInRequests = maxInRequests;
    }

    public long getMaxInBytes() {
        return maxInBytes;
    }

    public void setMaxInBytes(long maxInBytes) {
        this.maxInBytes = maxInBytes;
    }

    public PoolFactory getPoolFactory() {
        return poolFactory;
    }

    public void setPoolFactory(PoolFactory poolFactory) {
        this.poolFactory = poolFactory;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public boolean isFinishPendingImmediatelyWhenChannelClose() {
        return finishPendingImmediatelyWhenChannelClose;
    }

    public void setFinishPendingImmediatelyWhenChannelClose(boolean finishPendingImmediatelyWhenChannelClose) {
        this.finishPendingImmediatelyWhenChannelClose = finishPendingImmediatelyWhenChannelClose;
    }

    public PerfCallback getPerfCallback() {
        return perfCallback;
    }

    public void setPerfCallback(PerfCallback perfCallback) {
        this.perfCallback = perfCallback;
    }

    public long getMaxOutBytes() {
        return maxOutBytes;
    }

    public void setMaxOutBytes(long maxOutBytes) {
        this.maxOutBytes = maxOutBytes;
    }

    public Supplier<DecodeContext> getDecodeContextFactory() {
        return decodeContextFactory;
    }

    public void setDecodeContextFactory(Supplier<DecodeContext> decodeContextFactory) {
        this.decodeContextFactory = decodeContextFactory;
    }

    public ChannelListener getChannelListener() {
        return channelListener;
    }

    public void setChannelListener(ChannelListener channelListener) {
        this.channelListener = channelListener;
    }

    public boolean isServerHint() {
        return serverHint;
    }

    public void setServerHint(boolean serverHint) {
        this.serverHint = serverHint;
    }

}
