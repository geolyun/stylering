package com.stylering.auth;

public interface FirebaseTokenVerifier {
    VerifiedFirebaseToken verify(String idToken);
}
