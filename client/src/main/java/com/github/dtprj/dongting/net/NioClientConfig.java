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

import java.util.List;

/**
 * @author huangli
 */
public class NioClientConfig extends NioConfig {
    public List<HostPort> hostPorts;
    public int waitStartTimeout = 2000;
    public int[] connectRetryIntervals = {100, 1000, 5000, 10 * 1000, 20 * 1000, 30 * 1000, 60 * 1000};

    public NioClientConfig() {
        this.name = "DtNioClient";
        this.bizThreads = Runtime.getRuntime().availableProcessors();

        this.maxOutRequests = 2000;
        this.maxOutBytes = 32 * 1024 * 1024;
        this.maxInRequests = 100;
        this.maxInBytes = 32 * 1024 * 1024;
    }
}
