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
package org.apache.maven.archetype.ui.generation;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;

import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.exception.ArchetypeSelectionFailure;
import org.apache.maven.archetype.ui.ArchetypeDefinition;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.testing.PlexusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@PlexusTest
public class DefaultArchetypeSelectorTest {

    @Inject
    private DefaultArchetypeSelector selector;

    @Test
    public void testArchetypeCoordinatesInRequest() throws PrompterException, ArchetypeSelectionFailure {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeArtifactId("preset-artifactId");
        request.setArchetypeGroupId("preset-groupId");
        request.setArchetypeVersion("preset-version");

        ArchetypeSelectionQueryer queryer = Mockito.mock(ArchetypeSelectionQueryer.class);

        selector.setArchetypeSelectionQueryer(queryer);

        selector.selectArchetype(request, Boolean.TRUE, "");

        Mockito.verify(queryer);

        assertEquals("preset-groupId", request.getArchetypeGroupId());
        assertEquals("preset-artifactId", request.getArchetypeArtifactId());
        assertEquals("preset-version", request.getArchetypeVersion());
    }

    @Test
    public void testArchetypeArtifactIdInRequest() throws PrompterException, ArchetypeSelectionFailure {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeArtifactId("preset-artifactId");

        ArchetypeSelectionQueryer queryer = Mockito.mock(ArchetypeSelectionQueryer.class);

        selector.setArchetypeSelectionQueryer(queryer);

        selector.selectArchetype(request, Boolean.TRUE, "");

        Mockito.verify(queryer);

        assertEquals(DefaultArchetypeSelector.DEFAULT_ARCHETYPE_GROUPID, request.getArchetypeGroupId());
        assertEquals("preset-artifactId", request.getArchetypeArtifactId());
        assertEquals(DefaultArchetypeSelector.DEFAULT_ARCHETYPE_VERSION, request.getArchetypeVersion());
    }

    @Test
    public void testArchetypeArtifactIdNotInRequest() throws PrompterException, ArchetypeSelectionFailure {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();

        ArchetypeSelectionQueryer queryer = Mockito.mock(ArchetypeSelectionQueryer.class);

        Archetype archetype = new Archetype();
        archetype.setArtifactId("set-artifactId");
        archetype.setGroupId("set-groupId");
        archetype.setVersion("set-version");
        ArchetypeDefinition y = Mockito.any();
        Map<String, List<Archetype>> x = Mockito.any();
        Mockito.when(queryer.selectArchetype(x, y)).thenReturn(archetype);

        selector.setArchetypeSelectionQueryer(queryer);

        selector.selectArchetype(request, Boolean.TRUE, "");

        verify(queryer).selectArchetype(x, y);

        assertEquals("set-groupId", request.getArchetypeGroupId());
        assertEquals("set-artifactId", request.getArchetypeArtifactId());
        assertEquals("set-version", request.getArchetypeVersion());
    }

    @Test
    public void testArchetypeNotInRequestDefaultsInBatchMode() throws PrompterException, ArchetypeSelectionFailure {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();

        ArchetypeSelectionQueryer queryer = Mockito.mock(ArchetypeSelectionQueryer.class);

        selector.setArchetypeSelectionQueryer(queryer);

        selector.selectArchetype(request, Boolean.FALSE, "");

        Mockito.verify(queryer);

        assertEquals(DefaultArchetypeSelector.DEFAULT_ARCHETYPE_GROUPID, request.getArchetypeGroupId());
        assertEquals(DefaultArchetypeSelector.DEFAULT_ARCHETYPE_ARTIFACTID, request.getArchetypeArtifactId());
        assertEquals(DefaultArchetypeSelector.DEFAULT_ARCHETYPE_VERSION, request.getArchetypeVersion());
    }

    @Test
    public void testArchetypeNotInRequestDefaults() throws PrompterException, ArchetypeSelectionFailure {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();

        ArchetypeSelectionQueryer queryer = Mockito.mock(ArchetypeSelectionQueryer.class);
        Archetype archetype = new Archetype();
        archetype.setArtifactId("set-artifactId");
        archetype.setGroupId("set-groupId");
        archetype.setVersion("set-version");
        ArchetypeDefinition y = Mockito.any();
        Map<String, List<Archetype>> x = Mockito.any();
        Mockito.when(queryer.selectArchetype(x, y)).thenReturn(archetype);

        selector.setArchetypeSelectionQueryer(queryer);

        selector.selectArchetype(request, Boolean.TRUE, "");

        verify(queryer).selectArchetype(x, y);

        assertEquals("set-groupId", request.getArchetypeGroupId());
        assertEquals("set-artifactId", request.getArchetypeArtifactId());
        assertEquals("set-version", request.getArchetypeVersion());
    }
}
