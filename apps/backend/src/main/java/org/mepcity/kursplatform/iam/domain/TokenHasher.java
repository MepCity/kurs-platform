package org.mepcity.kursplatform.iam.domain;

public interface TokenHasher {

    String hash(String tokenValue);

    String hashWithPepper(String tokenValue, String pepper);
}
