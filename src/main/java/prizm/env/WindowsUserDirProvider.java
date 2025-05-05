/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package prizm.env;

import prizm.http.API;
import prizm.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

public class WindowsUserDirProvider extends DesktopUserDirProvider {

    private static final String PRIZM_USER_HOME = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "Prizm").toString();

    @Override
    public String getUserHomeDir() {
        return PRIZM_USER_HOME;
    }

    @Override
    public boolean startNativeModule (String moduleName, String arguments) {
        Process process = null;
        final File executable = Paths.get("native", "modules", moduleName, "launcher.exe").toFile();
        if (!executable.exists()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", not found!");
            return false;
        }
        if (!executable.canRead()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", can't read!");
            return false;
        }
        if (!executable.canExecute()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", can't execute!");
            return false;
        }
        try {
            process = Runtime.getRuntime().exec("cmd /C " + executable.getPath() + " " + arguments);
        } catch (IOException e) {
            e.printStackTrace();
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", IO exception!");
            return false;
        }
        return process != null && process.isAlive();
    }
}
