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

/*
 *  Copyright 2007 rafale.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.catalog.io.xpp3.ArchetypeCatalogXpp3Writer;
import org.apache.maven.archetype.test.TestSessions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.testing.di.MavenDIExtension.getTestFile;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MavenDITest
public class LocalCatalogArchetypeDataSourceTest {

    @Inject
    private ArchetypeManager archetype;

    @BeforeEach
    protected void setUp() throws Exception {

        File catalogDirectory = getTestFile("target/test-classes/repositories/test-catalog");
        catalogDirectory.mkdirs();

        ArchetypeCatalog catalog = new ArchetypeCatalog();
        Archetype generatedArchetype = new Archetype();
        generatedArchetype.setGroupId("groupId");
        generatedArchetype.setArtifactId("artifactId");
        generatedArchetype.setVersion("1");
        generatedArchetype.setRepository("http://localhost:0/repo/");
        catalog.addArchetype(generatedArchetype);

        File catalogFile = new File(catalogDirectory, "archetype-catalog.xml");
        ArchetypeCatalogXpp3Writer catalogWriter = new ArchetypeCatalogXpp3Writer();
        try (Writer writer = new FileWriter(catalogFile)) {
            catalogWriter.write(writer, catalog);
        }
    }

    @Test
    public void testLocalCatalog() {

        Session session = TestSessions.create(getTestFile("target/test-classes/repositories/test-catalog"));

        ArchetypeCatalog result = archetype.getLocalCatalog(session);

        assertEquals(1, result.getArchetypes().size());
        assertEquals("groupId", result.getArchetypes().get(0).getGroupId());
        assertEquals("artifactId", result.getArchetypes().get(0).getArtifactId());
        assertEquals("1", result.getArchetypes().get(0).getVersion());
        assertEquals("http://localhost:0/repo/", result.getArchetypes().get(0).getRepository());
    }
}
