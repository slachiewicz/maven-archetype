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
package org.apache.maven.archetype.generator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeGenerationResult;
import org.apache.maven.archetype.common.ArchetypeArtifactManager;
import org.apache.maven.archetype.exception.ArchetypeException;
import org.apache.maven.archetype.exception.ArchetypeGenerationFailure;
import org.apache.maven.archetype.exception.ArchetypeNotDefined;
import org.apache.maven.archetype.exception.InvalidPackaging;
import org.apache.maven.archetype.exception.UnknownArchetype;
import org.apache.maven.archetype.old.OldArchetype;

@Named
@Singleton
public class DefaultArchetypeGenerator implements ArchetypeGenerator {

    private ArchetypeArtifactManager archetypeArtifactManager;

    private FilesetArchetypeGenerator filesetGenerator;

    private OldArchetype oldArchetype;

    @Inject
    public DefaultArchetypeGenerator(
            ArchetypeArtifactManager archetypeArtifactManager,
            FilesetArchetypeGenerator filesetGenerator,
            OldArchetype oldArchetype) {
        this.archetypeArtifactManager = archetypeArtifactManager;
        this.filesetGenerator = filesetGenerator;
        this.oldArchetype = oldArchetype;
    }

    private File getArchetypeFile(ArchetypeGenerationRequest request) throws ArchetypeException {
        if (!isArchetypeDefined(request)) {
            throw new ArchetypeNotDefined("The archetype is not defined");
        }

        List<RemoteRepository> repos = new ArrayList<>(request.getRemoteRepositories());

        if (request != null && request.getArchetypeRepository() != null) {
            RemoteRepository remoteRepo = request.getSession()
                    .createRemoteRepository(
                            request.getArchetypeArtifactId() + "-repo", request.getArchetypeRepository());

            repos.add(remoteRepo);
        }

        if (!archetypeArtifactManager.exists(
                request.getArchetypeGroupId(),
                request.getArchetypeArtifactId(),
                request.getArchetypeVersion(),
                repos,
                request.getSession())) {
            throw new UnknownArchetype("The desired archetype does not exist (" + request.getArchetypeGroupId() + ":"
                    + request.getArchetypeArtifactId() + ":" + request.getArchetypeVersion() + ")");
        }

        return archetypeArtifactManager.getArchetypeFile(
                request.getArchetypeGroupId(),
                request.getArchetypeArtifactId(),
                request.getArchetypeVersion(),
                repos,
                request.getSession());
    }

    private void generateArchetype(ArchetypeGenerationRequest request, File archetypeFile) throws ArchetypeException {
        if (archetypeArtifactManager.isFileSetArchetype(archetypeFile)) {
            processFileSetArchetype(request, archetypeFile);
        } else if (archetypeArtifactManager.isOldArchetype(archetypeFile)) {
            processOldArchetype(request, archetypeFile);
        } else {
            throw new ArchetypeGenerationFailure("The defined artifact is not an archetype: " + archetypeFile);
        }
    }

    /** Common. */
    public String getPackageAsDirectory(String packageName) {
        return StringUtils.replace(packageName, ".", "/");
    }

    private boolean isArchetypeDefined(ArchetypeGenerationRequest request) {
        return StringUtils.isNotEmpty(request.getArchetypeGroupId())
                && StringUtils.isNotEmpty(request.getArchetypeArtifactId())
                && StringUtils.isNotEmpty(request.getArchetypeVersion());
    }

    /** FileSetArchetype. */
    private void processFileSetArchetype(ArchetypeGenerationRequest request, File archetypeFile)
            throws ArchetypeException {
        filesetGenerator.generateArchetype(request, archetypeFile);
    }

    private void processOldArchetype(ArchetypeGenerationRequest request, File archetypeFile)
            throws ArchetypeGenerationFailure, InvalidPackaging {
        oldArchetype.createArchetype(request, archetypeFile);
    }

    @Override
    public void generateArchetype(
            ArchetypeGenerationRequest request, File archetypeFile, ArchetypeGenerationResult result) {
        try {
            generateArchetype(request, archetypeFile);
        } catch (ArchetypeException e) {
            result.setCause(e);
        }
    }

    @Override
    public void generateArchetype(ArchetypeGenerationRequest request, ArchetypeGenerationResult result) {
        try {
            File archetypeFile = getArchetypeFile(request);

            generateArchetype(request, archetypeFile, result);
        } catch (ArchetypeException ex) {
            result.setCause(ex);
        }
    }
}
