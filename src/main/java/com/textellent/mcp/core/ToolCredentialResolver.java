package com.textellent.mcp.core;

import com.textellent.mcp.security.JwtClaimsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolCredentialResolver {

    private static final Logger logger = LoggerFactory.getLogger(ToolCredentialResolver.class);

    private final JwtClaimsExtractor jwtClaimsExtractor;

    public ToolCredentialResolver(JwtClaimsExtractor jwtClaimsExtractor) {
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    public ToolExecutionContext resolve(Map<String, Object> arguments) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String finalAuthCode = null;
        String finalPartnerCode = arguments != null ? (String) arguments.get("partnerClientCode") : null;

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String jwtAuthCode = jwtClaimsExtractor.extractAuthCode(jwt);
            if (finalPartnerCode != null && !finalPartnerCode.isEmpty()) {
                String partnerAuthCode = jwtClaimsExtractor.extractPartnerAuthCode(jwt);
                if (partnerAuthCode != null && !partnerAuthCode.isEmpty()) {
                    finalAuthCode = partnerAuthCode;
                    logger.debug("Using partner_auth_code for partnerClientCode={}", finalPartnerCode);
                }
            } else {
                finalAuthCode = jwtAuthCode;
            }
        }

        if ((finalAuthCode == null || finalAuthCode.isEmpty()) && arguments != null) {
            // Header fallback parity for non-JWT modes: allow args fallback
            finalAuthCode = (String) arguments.get("authCode");
        }

        return new ToolExecutionContext(finalAuthCode, finalPartnerCode);
    }
}
