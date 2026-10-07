package com.authentisign.desktop.security.Utils;

import java.io.File;

//מחלקה ליצירת התיקייה בה נשמרים המפתחות
public class FilePaths {

    public static String getKeysDirectory() {
        String userHome = System.getProperty("user.home");
        File keysDir = new File(userHome, ".authentisign" + File.separator + "keys");

        if (!keysDir.exists()) {
            keysDir.mkdirs();
        }
        return keysDir.getAbsolutePath();
    }
}
