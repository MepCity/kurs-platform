package org.mepcity.kursplatform.org.application;
import java.util.UUID;
@FunctionalInterface public interface OrganizationListRateLimiter { void check(UUID actorUserId); }
