package com.recipe.ai.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Component;

@Component
public class DefaultFirebaseVerifier implements FirebaseVerifier {
    @Override
    public VerifiedUser verifyIdToken(String idToken) throws FirebaseAuthException {
        FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idToken);
        return new VerifiedUser(token.getUid(), token.getEmail());
    }
}
