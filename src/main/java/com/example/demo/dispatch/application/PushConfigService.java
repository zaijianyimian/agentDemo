package com.example.demo.dispatch.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.persistence.PushConfigMapper;
import com.example.demo.shared.context.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-scoped push preferences. Platform transport credentials are not exposed here. */
@Service
@RequiredArgsConstructor
public class PushConfigService {
    private final PushConfigMapper mapper;
    private final CurrentUserContext currentUser;

    @Transactional
    public PushConfig getOrCreate() {
        long userId = currentUser.requireUserId();
        PushConfig existing = findCurrent(userId);
        if (existing != null) {
            return existing;
        }
        PushConfig created = defaults(userId);
        mapper.insert(created);
        return created;
    }

    @Transactional
    public PushConfig update(PushConfig input) {
        long userId = currentUser.requireUserId();
        PushConfig target = findCurrent(userId);
        if (target == null) {
            target = defaults(userId);
        }
        target.setPushEmail(input.getPushEmail());
        target.setPushThreshold(input.getPushThreshold());
        target.setBatchCron(input.getBatchCron());
        target.setImmediateEnabled(input.getImmediateEnabled());
        target.setResultRetentionDays(input.getResultRetentionDays());
        if (target.getId() == null) {
            mapper.insert(target);
        } else {
            mapper.updateById(target);
        }
        return target;
    }

    private PushConfig findCurrent(long userId) {
        return mapper.selectOne(new LambdaQueryWrapper<PushConfig>()
                .eq(PushConfig::getUserId, userId)
                .last("LIMIT 1"));
    }

    private PushConfig defaults(long userId) {
        return PushConfig.builder()
                .userId(userId)
                .pushThreshold("medium")
                .batchCron("0 0 9 * * ?")
                .immediateEnabled(true)
                .resultRetentionDays(30)
                .build();
    }
}
