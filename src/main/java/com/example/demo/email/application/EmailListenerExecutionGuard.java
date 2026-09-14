package com.example.demo.email.application;

import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.context.ExecutionPolicy;
import com.example.demo.shared.context.PersistedOwnerExecutionContextFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Revalidates the persisted owner and configuration before background provider access. */
@Service
public class EmailListenerExecutionGuard {

    private final EmailConfigMapper emailConfigMapper;
    private final PersistedOwnerExecutionContextFactory executionContexts;

    public EmailListenerExecutionGuard(
            EmailConfigMapper emailConfigMapper,
            PersistedOwnerExecutionContextFactory executionContexts) {
        this.emailConfigMapper = emailConfigMapper;
        this.executionContexts = executionContexts;
    }

    /**
     * Returns false when the owner is disabled, the mailbox is disabled/deleted, or the running
     * listener was created from an older configuration snapshot.
     */
    public boolean mayAccessProvider(EmailConfig runningConfig) {
        if (runningConfig == null || runningConfig.getId() == null || runningConfig.getUserId() == null) {
            return false;
        }
        try {
            executionContexts.forPersistedOwner(
                    runningConfig.getUserId(),
                    "email-listener-revalidate",
                    ExecutionPolicy.readOnly());
        } catch (RuntimeException rejectedOwner) {
            return false;
        }

        EmailConfig latest = emailConfigMapper.selectById(runningConfig.getId());
        return latest != null
                && Boolean.TRUE.equals(latest.getEnabled())
                && Objects.equals(runningConfig.getUserId(), latest.getUserId())
                && sameConfigurationVersion(runningConfig, latest);
    }

    private boolean sameConfigurationVersion(EmailConfig running, EmailConfig latest) {
        if (running.getUpdateTime() != null || latest.getUpdateTime() != null) {
            return Objects.equals(running.getUpdateTime(), latest.getUpdateTime());
        }
        return Objects.equals(running.getEmail(), latest.getEmail())
                && Objects.equals(running.getHost(), latest.getHost())
                && Objects.equals(running.getProtocol(), latest.getProtocol())
                && Objects.equals(running.getProvider(), latest.getProvider())
                && Objects.equals(running.getListenMode(), latest.getListenMode())
                && Objects.equals(running.getProviderSettings(), latest.getProviderSettings())
                && Objects.equals(running.getEnabled(), latest.getEnabled())
                && Objects.equals(running.getFolder(), latest.getFolder())
                && Objects.equals(running.getPollInterval(), latest.getPollInterval())
                && Objects.equals(running.getListenStartTime(), latest.getListenStartTime())
                && Objects.equals(running.getListenEndTime(), latest.getListenEndTime());
    }
}
