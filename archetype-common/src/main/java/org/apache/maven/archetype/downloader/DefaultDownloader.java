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
package org.apache.maven.archetype.downloader;

import java.io.File;
import java.util.List;

import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.DownloadedArtifact;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.services.ArtifactResolverException;

@Named
@Singleton
public class DefaultDownloader implements Downloader {

    @Override
    public File download(
            String groupId,
            String artifactId,
            String version,
            List<RemoteRepository> remoteRepositories,
            Session session)
            throws DownloadException {

        ArtifactCoordinates coordinates = session.createArtifactCoordinates(groupId, artifactId, version, "jar");
        try {
            DownloadedArtifact result = session.resolveArtifact(coordinates, remoteRepositories);
            return result.getPath().toFile();
        } catch (ArtifactResolverException e) {
            throw new DownloadException("Could not resolve artifact " + artifactId, e);
        }
    }
}
