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
package com.github.dtprj.dongting.raft.store;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
class StoreUtil {
    static void prepareNextRead(ByteBuffer buf) {
        if (buf.hasRemaining()) {
            ByteBuffer temp = buf.slice();
            buf.clear();
            buf.put(temp);
        } else {
            buf.clear();
        }
    }

    static int calcRetryInterval(int currentRetryCount, int[] retryIntervals) {
        if (retryIntervals == null) {
            return -1;
        }
        if (currentRetryCount >= retryIntervals.length) {
            return retryIntervals[retryIntervals.length - 1];
        }
        return retryIntervals[currentRetryCount];
    }
}
