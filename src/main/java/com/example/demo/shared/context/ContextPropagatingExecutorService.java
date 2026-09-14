package com.example.demo.shared.context;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** ExecutorService boundary that captures the immutable execution context at submission time. */
public final class ContextPropagatingExecutorService extends AbstractExecutorService {
    private final ExecutorService delegate;

    public ContextPropagatingExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate executor required");
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "Command required");
        Runnable captured = ExecutionContextScope.current()
                .<Runnable>map(context -> ExecutionContextScope.wrap(context, command))
                .orElse(command);
        delegate.execute(captured);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
