package com.example.siteplatform.siteaccess.service;

import java.util.List;

interface VisitorLegacyReencryptionStore {
    void verifyEncryptedColumnWhitelist();

    boolean migrationMarkerExists(boolean lock);

    List<VisitorLegacyReencryptionTool.EncryptedCell> loadEncryptedCells(boolean lock);

    int replaceCiphertext(VisitorLegacyReencryptionTool.EncryptedCell cell, String replacement);

    void insertMigrationMarker();
}
