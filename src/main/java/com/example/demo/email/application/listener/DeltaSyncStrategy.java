package com.example.demo.email.application.listener;

import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import org.springframework.stereotype.Component;
/**
 *延迟同步策略
 */

/**
 * 增量同步监听策略。
 * 当前实现复用轮询策略（provider 自身的 delta 接口），仅作为不同 {@link ListenMode} 的入口分派。
 */
@Component
public class DeltaSyncStrategy implements ListenStrategy {

    private final PollingStrategy pollingStrategy;

    public DeltaSyncStrategy(PollingStrategy pollingStrategy) {
        this.pollingStrategy = pollingStrategy;
    }

    @Override
    public ListenMode mode() {
        return ListenMode.DELTA_SYNC;
    }

    @Override
    public void start(EmailConfig config, MailSourceAdapter adapter) {
        pollingStrategy.start(config, adapter);
    }

    @Override
    public void stop(Long configId) {
        pollingStrategy.stop(configId);
    }
}
