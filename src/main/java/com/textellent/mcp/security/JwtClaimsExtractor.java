package com.textellent.mcp.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Utility class to extract custom claims from JWT tokens.
 * Supports multi-tenant architecture by extracting authCode and partnerClientCode from JWT.
 */
@Component
public class JwtClaimsExtractor {

    /**
     * Extract authCode from JWT token.
     * The authCode is always fetched from client_contact_details table and added to JWT by OAuth2 server.
     * This is the primary authentication credential for backend API calls.
     *
     * @param jwt The JWT token
     * @return The authCode, or null if not found
     */
    public String extractAuthCode(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Primary claim name for client's own auth_code (from client_contact_details)
        String authCode = jwt.getClaimAsString("auth_code");
        if (authCode == null) {
            authCode = jwt.getClaimAsString("authCode");
        }

        return authCode;
    }

    /**
     * Extract partner authCode from JWT token.
     * The partner authCode is optionally fetched from partner table and added to JWT.
     * This is used when a client wants to act on behalf of their offices/sub-clients.
     * The partnerClientCode must be provided as a header to use this.
     *
     * @param jwt The JWT token
     * @return The partner authCode, or null if not found
     */
    public String extractPartnerAuthCode(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Partner auth_code claim (from partner table)
        String partnerAuthCode = jwt.getClaimAsString("partner_auth_code");
        if (partnerAuthCode == null) {
            partnerAuthCode = jwt.getClaimAsString("partnerAuthCode");
        }
        if (partnerAuthCode == null) {
            partnerAuthCode = jwt.getClaimAsString("partnerauthCode");
        }

        return partnerAuthCode;
    }

    /**
     * Extract partnerClientCode from JWT token.
     * Checks multiple possible claim names for flexibility.
     *
     * @param jwt The JWT token
     * @return The partnerClientCode, or null if not found
     */
    public String extractPartnerClientCode(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names
        String partnerCode = jwt.getClaimAsString("partner_client_code");
        if (partnerCode == null) {
            partnerCode = jwt.getClaimAsString("partnerClientCode");
        }
        if (partnerCode == null) {
            partnerCode = jwt.getClaimAsString("textellent_partner_code");
        }

        return partnerCode;
    }

    /**
     * Extract tenant ID from JWT token.
     * Used for multi-tenancy and rate limiting.
     *
     * @param jwt The JWT token
     * @return The tenant ID, or null if not found
     */
    public String extractTenantId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("tenantId");
        }
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("organization_id");
        }
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("client_id");
        }

        return tenantId;
    }

    /**
     * Extract user ID from JWT token.
     * Used for audit logging.
     *
     * @param jwt The JWT token
     * @return The user ID, or null if not found
     */
    public String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names (standard + custom)
        String userId = jwt.getClaimAsString("sub");  // Standard JWT subject claim
        if (userId == null) {
            userId = jwt.getClaimAsString("user_id");
        }
        if (userId == null) {
            userId = jwt.getClaimAsString("userId");
        }
        if (userId == null) {
            userId = jwt.getClaimAsString("email");
        }

        return userId;
    }
}
