package com.example.demo.shared.context;

/** Shared port for opening background work from an owner read from trusted persistence. */
public interface PersistedOwnerExecutionContextFactory {

    ExecutionContext forPersistedOwner(Long ownerId, String trigger, ExecutionPolicy policy);
}
