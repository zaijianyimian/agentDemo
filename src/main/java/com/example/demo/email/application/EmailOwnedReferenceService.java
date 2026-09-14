package com.example.demo.email.application;

import com.example.demo.email.domain.OwnedEmailConfigRef;
import com.example.demo.email.persistence.EmailConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Internal boundary for validating persisted email owners before opening a user scope. */
@Service
@RequiredArgsConstructor
public class EmailOwnedReferenceService {
    private final EmailConfigMapper mapper;

    public OwnedEmailConfigRef requireEnabledConfig(long configId) {
        OwnedEmailConfigRef reference = mapper.selectOwnedRefForInternalValidation(configId);
        if (reference == null) {
            throw new IllegalArgumentException("email_id must reference an enabled email configuration");
        }
        return reference;
    }
}
