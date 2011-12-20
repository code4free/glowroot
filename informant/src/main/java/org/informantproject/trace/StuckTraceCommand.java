/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.trace;

import java.lang.ref.WeakReference;

/**
 * Designed to be scheduled and run as soon as the trace exceeds a given threshold.
 * 
 * If the {@link Trace} has already completed when this is run then it does nothing.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
class StuckTraceCommand implements Runnable {

    // since it's possible for this scheduled command to live for a while
    // after the trace has completed, a weak reference is used to make sure
    // it won't prevent the (larger) trace structure from being garbage collected
    private final WeakReference<Trace> traceHolder;

    private final TraceCollector traceCollector;

    StuckTraceCommand(Trace trace, TraceCollector traceCollector) {
        this.traceHolder = new WeakReference<Trace>(trace);
        this.traceCollector = traceCollector;
    }

    public void run() {
        Trace trace = traceHolder.get();
        if (trace == null || trace.isCompleted()) {
            // already completed
            return;
        }
        if (trace.setStuck()) {
            // already marked as stuck
            return;
        }
        traceCollector.collectStuckTrace(trace);
    }
}
