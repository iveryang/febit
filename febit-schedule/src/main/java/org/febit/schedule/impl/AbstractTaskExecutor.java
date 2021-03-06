/**
 * Copyright 2013-present febit.org (support@febit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.febit.schedule.impl;

import org.febit.lang.Time;
import org.febit.schedule.InitableTask;
import org.febit.schedule.MatchableTask;
import org.febit.schedule.Task;
import org.febit.schedule.TaskContext;
import org.febit.schedule.TaskExecutor;

/**
 *
 * @author zqq90
 */
public abstract class AbstractTaskExecutor implements TaskExecutor {

    protected final Object lockThis = new Object();
    protected final Task task;
    protected final DefaultTaskContext taskContext;
    protected volatile boolean requestedStop;
    protected volatile boolean requestedPause;
    protected volatile boolean running;
    protected volatile Time time;

    public AbstractTaskExecutor(Task task) {
        this.task = task;
        this.taskContext = new DefaultTaskContext(this);
        initTask();
    }

    protected final void initTask() {
        if (task instanceof InitableTask) {
            ((InitableTask) task).init(this);
        }
    }

    protected boolean beforeRun(Time time) {
        if (this.task instanceof MatchableTask) {
            if (!((MatchableTask) this.task).match(time)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void runIfNot(Time time) {
        if (!beforeRun(time)) {
            return;
        }
        this.time = time;
        this.requestedStop = false;
        if (!running) {
            synchronized (lockThis) {
                if (!running) {
                    Runnable runnable = createRunnable(time);
                    running = true;
                    try {
                        run(runnable);
                    } catch (Exception e) {
                        running = false;
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    protected Runnable createRunnable(Time time) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    task.execute(taskContext);
                } finally {
                    AbstractTaskExecutor.this.onTaskFinished();
                }
            }
        };
    }

    protected void onTaskFinished() {
        synchronized (lockThis) {
            running = false;
        }
    }

    protected abstract void run(Runnable runnable) throws Exception;

    protected abstract void tillStop();

    @Override
    public void stopAndWait() {
        synchronized (lockThis) {
            if (!this.requestedStop) {
                askforStop();
            }
            if (running) {
                tillStop();
                running = false;
            }
        }
    }

    @Override
    public void askforStop() {
        this.requestedStop = true;
        goonIfPaused();
    }

    @Override
    public void goonIfPaused() {
        if (this.requestedPause) {
            synchronized (lockThis) {
                if (this.requestedPause) {
                    this.requestedPause = false;
                    this.lockThis.notifyAll();
                }
            }
        }
    }

    @Override
    public void askforPause() {
        this.requestedPause = true;
    }

    protected void pauseIfRequested() {
        if (this.requestedPause) {
            synchronized (this.lockThis) {
                if (this.requestedPause) {
                    try {
                        this.lockThis.wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }
    }

    private boolean isRequestedStopOrPause() {
        return this.requestedStop || this.requestedPause;
    }

    @Override
    public Task getTask() {
        return task;
    }

    @Override
    public int getState() {
        if (requestedStop) {
            return running ? STATE_STOPPING : STATE_STOPPED;
        }
        if (requestedPause) {
            return running ? STATE_PAUSING : STATE_PAUSED;
        }
        return running ? STATE_RUNNING : STATE_HOLDING;
    }

    protected static class DefaultTaskContext implements TaskContext {

        protected final AbstractTaskExecutor taskExecutor;

        public DefaultTaskContext(AbstractTaskExecutor taskExecutor) {
            this.taskExecutor = taskExecutor;
        }

        @Override
        public void pauseIfRequested() {
            this.taskExecutor.pauseIfRequested();
        }

        @Override
        public boolean isRequestedStop() {
            return this.taskExecutor.requestedStop;
        }

        @Override
        public Time getTime() {
            return this.taskExecutor.time;
        }

        @Override
        public boolean isRequestedPause() {
            return this.taskExecutor.requestedPause;
        }

        @Override
        public boolean isRequestedStopOrPause() {
            return this.taskExecutor.isRequestedStopOrPause();
        }
    }
}
