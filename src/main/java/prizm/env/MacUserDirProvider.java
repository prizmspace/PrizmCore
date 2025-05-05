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

import prizm.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class MacUserDirProvider extends UnixUserDirProvider {
    @Override
    public boolean startNativeModule (String moduleName, String arguments) {
        Process process = null;
        // We should have unique app name for each module (unique among all Mac applications, to prevent MacOS from messing everything up)
        final File executable = Paths.get("native", "modules", moduleName, "native_module_"+moduleName+"_launcher.app").toFile();
        if (!executable.exists()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", not found! Path: " + executable.getPath() + " Absolute: " + executable.getAbsolutePath());
            return false;
        }
        if (!executable.canRead()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", can't read! Path: " + executable.getPath() + " Absolute: " + executable.getAbsolutePath());
            return false;
        }
        if (!executable.canExecute()) {
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", can't execute! Path: " + executable.getPath() + " Absolute: " + executable.getAbsolutePath());
            return false;
        }
        try {
            // Do not use "-a" flag, because with this flag MacOS ignores provided path to .app and starting last started .app with similar name.
            process = Runtime.getRuntime().exec("open " + executable.getAbsolutePath() + " --args " + arguments);
        } catch (IOException e) {
            e.printStackTrace();
            Logger.logWarningMessage("Failed to launch Native Module: " + moduleName + ", IO exception!");
            return false;
        }
        return process != null && process.isAlive();
    }
}
