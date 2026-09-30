/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.archetype.test;

import java.io.File;

import org.apache.maven.api.Session;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Provides;
import org.apache.maven.impl.standalone.ApiRunner;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.io.PathProcessor;
import org.eclipse.aether.transport.apache.ApacheTransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;

/**
 * Creates standalone Maven 4 API sessions for the tests. The standalone runner provides no transporter,
 * so the {@code file} and {@code http(s)} ones are registered here.
 */
public final class TestSessions {

    private TestSessions() {}

    public static Session create(File localRepository) {
        return ApiRunner.createSession(injector -> injector.bindImplicit(Transporters.class), localRepository.toPath());
    }

    /** Transporters the standalone session does not register. */
    public static class Transporters {
        @Provides
        @Named("file")
        static TransporterFactory file() {
            return new FileTransporterFactory();
        }

        @Provides
        @Named("http")
        static TransporterFactory http(
                org.eclipse.aether.spi.connector.transport.http.ChecksumExtractor checksumExtractor,
                PathProcessor pathProcessor) {
            return new ApacheTransporterFactory(checksumExtractor, pathProcessor);
        }
    }
}
