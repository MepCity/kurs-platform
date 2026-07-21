package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.SessionInfoService;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

/** See IamAuthController's javadoc: conditional on DataSource for the same reason. */
@RestController
@ConditionalOnBean(DataSource.class)
@RequestMapping("/api/v1/iam/sessions")
public class IamSessionController {

    private final SessionInfoService sessionInfoService;

    public IamSessionController(SessionInfoService sessionInfoService) {
        this.sessionInfoService = sessionInfoService;
    }

    @GetMapping("/me")
    public ResponseEntity<SessionInfoResponse> getSessionInfo(
            @RequestHeader("Authorization") String authorization) {
        String accessToken = extractBearerToken(authorization);
        var result = sessionInfoService.resolveSession(accessToken);
        return ResponseEntity.ok(SessionInfoResponse.from(result));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IamException("UNAUTHENTICATED", "Authorization başlığı geçersiz.");
        }
        return authorization.substring("Bearer ".length());
    }
}
