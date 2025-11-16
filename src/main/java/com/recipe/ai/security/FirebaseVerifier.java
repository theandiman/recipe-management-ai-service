package com.recipe.ai.security;

public interface FirebaseVerifier {
    // Represents a verified user
    record VerifiedUser(String uid, String email) { }

    VerifiedUser verifyIdToken(String idToken) throws Exception;
}
