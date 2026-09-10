package com.example.siteplatform.siteaccess.service;

final class VisitorIdentitySupport {
    private VisitorIdentitySupport() {}

    static String hash(String purpose, VisitorSessionService.VisitorSessionContext context,
                       VisitorDataCryptoService crypto, VisitorSessionService sessions) {
        return crypto.fingerprint("site-access:" + purpose + ":v1",
                context.appId() + ":" + sessions.decryptOpenid(context));
    }
}
