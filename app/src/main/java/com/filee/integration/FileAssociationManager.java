package com.filee.integration;

import com.filee.utils.Logger;
import com.filee.utils.PlatformUtils;

import java.util.List;

public class FileAssociationManager {

    private static final String TAG = "FileAssociationManager";

    /**
     * Prepares Windows ProgID and file association entries.
     * Implementation scheduled for Phase 5.
     */
    public boolean registerAssociations(List<String> extensions) {
        if (!PlatformUtils.isWindows()) {
            return false;
        }
        Logger.info(TAG, "Queued associations for: " + String.join(", ", extensions));
        return true;
    }
}
