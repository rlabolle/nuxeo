/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thomas Roger
 *
 */

package org.nuxeo.runtime.test.runner;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

/**
 * @since 9.3
 */
public class LogFeature extends SimpleFeature {

    protected static final String CONSOLE_APPENDER_NAME = "CONSOLE";

    protected Priority consoleThresold;

    public void hideWarningFromConsoleLog() {
        Logger rootLogger = Logger.getRootLogger();
        ConsoleAppender consoleAppender = (ConsoleAppender) rootLogger.getAppender(CONSOLE_APPENDER_NAME);
        consoleThresold = consoleAppender.getThreshold();
        consoleAppender.setThreshold(Level.ERROR);
    }

    public void restoreConsoleLog() {
        if (consoleThresold == null) {
            return;
        }
        Logger rootLogger = Logger.getRootLogger();
        ConsoleAppender consoleAppender = (ConsoleAppender) rootLogger.getAppender(CONSOLE_APPENDER_NAME);
        consoleAppender.setThreshold(consoleThresold);
        consoleThresold = null;
    }

}