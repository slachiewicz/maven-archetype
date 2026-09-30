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
package org.apache.maven.archetype.source;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.services.MavenException;
import org.apache.maven.api.services.Transport;
import org.apache.maven.api.services.TransportProvider;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;

@Named("remote-catalog")
@Singleton
public class RemoteCatalogArchetypeDataSource extends CatalogArchetypeDataSource implements ArchetypeDataSource {

    /**
     * Id of the repository used to download catalog file. Proxy or authentication info can
     * be setup in settings.xml.
     */
    public static final String ARCHETYPE_REPOSITORY_ID = "archetype";

    public static final String CENTRAL_REPOSITORY_ID = "central";

    @Override
    public ArchetypeCatalog getArchetypeCatalog(Session session, List<RemoteRepository> remoteRepositories)
            throws ArchetypeDataSourceException {

        RemoteRepository repository = getRemoteRepo(remoteRepositories);
        if (repository == null) {
            throw new ArchetypeDataSourceException(
                    "No remote repository found to download " + ARCHETYPE_CATALOG_FILENAME);
        }

        // the Maven 4 API has no metadata resolution: the catalog is fetched directly, without the local
        // repository cache, update policy and checksum verification the Maven 3 resolver applied
        try (Transport transport = session.getService(TransportProvider.class).transport(session, repository)) {
            Optional<String> catalog = transport.getString(URI.create(ARCHETYPE_CATALOG_FILENAME));
            if (!catalog.isPresent()) {
                throw new ArchetypeDataSourceException(
                        ARCHETYPE_CATALOG_FILENAME + " not found in repository " + repository.getId());
            }
            return readCatalog(new StringReader(catalog.get()));
        } catch (IOException | MavenException e) {
            throw new ArchetypeDataSourceException(e);
        }
    }

    private RemoteRepository getRemoteRepo(List<RemoteRepository> remoteRepositories) {

        if (remoteRepositories == null || remoteRepositories.isEmpty()) {
            return null;
        }

        for (RemoteRepository remoteRepository : remoteRepositories) {
            if (ARCHETYPE_REPOSITORY_ID.equals(remoteRepository.getId())) {
                return remoteRepository;
            }

            if (CENTRAL_REPOSITORY_ID.equals(remoteRepository.getId())) {
                return remoteRepository;
            }
        }

        return null;
    }

    @Override
    public File updateCatalog(Session session, Archetype archetype) throws ArchetypeDataSourceException {
        throw new ArchetypeDataSourceException("Not supported yet.");
    }
}
