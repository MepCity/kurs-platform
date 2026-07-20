package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class ContextSelectionService {

    private final IamAuthRepository repository;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final IamTransactionExecutor transactionExecutor;

    public ContextSelectionService(IamAuthRepository repository, TokenHasher tokenHasher, Clock clock,
                                   IamTransactionExecutor transactionExecutor) {
        this.repository = repository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
    }

    public List<ContextSelectionSummary> listContextSelections(String contextSelectionTokenValue) {
        String tokenHash = tokenHasher.hash(contextSelectionTokenValue);
        return transactionExecutor.executeInIamAuthScope(
                OperationCode.CONTEXT_SELECTION_LIST,
                IamAuthScopeContext.bootstrapContextToken(tokenHash),
                () -> resolveSummaries(tokenHash));
    }

    private List<ContextSelectionSummary> resolveSummaries(String tokenHash) {
        Instant now = clock.instant();
        ContextSelectionToken token = repository.findContextSelectionTokenByHash(tokenHash)
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Context selection token geçersiz."));
        if (!token.isUsable(now)) {
            throw new IamException("UNAUTHENTICATED", "Context selection token süresi dolmuş veya tüketilmiş.");
        }

        transactionExecutor.refreshIamAuthScope(
                IamAuthScopeContext.actorAndDevice(token.userId(), token.trustedDeviceId()));

        // Same liveness invariant activation/session-info enforce: the bootstrap token_hash read
        // above is not final authorization — a device revoked after the context token was issued
        // must still fail closed here, before any membership/organization summary is ever queried.
        // findTrustedDeviceById filters by (userId, deviceId) together, so a device that belongs to
        // a different user or a mismatched id is already indistinguishable from "not found" here.
        repository.findTrustedDeviceById(token.userId(), token.trustedDeviceId())
                .filter(TrustedDevice::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Güvenilir cihaz bulunamadı veya iptal edilmiş."));

        User user = repository.findUserById(token.userId())
                .orElseThrow(() -> new IamException("ACCOUNT_NOT_READY", "Kullanıcı bulunamadı."));
        if (!user.isActive()) {
            throw new IamException("ACCOUNT_NOT_READY", "Kullanıcı hesabı aktif değil.");
        }

        return repository.findContextSelectionSummaries(user.id());
    }
}
