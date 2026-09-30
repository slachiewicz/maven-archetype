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
package org.apache.maven.archetype.old;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.testing.MavenDITest;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeVelocity;
import org.apache.maven.archetype.exception.InvalidPackaging;
import org.apache.maven.archetype.test.TestSessions;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.junit.jupiter.api.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

import static org.apache.maven.testing.di.MavenDIExtension.getTestFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
@MavenDITest
public class ArchetypeTest {

    @Inject
    private OldArchetype archetype;

    @Inject
    private ModelXmlFactory modelXmlFactory;

    @Inject
    private ArchetypeVelocity velocity;

    @Test
    public void testArchetype() throws Exception {
        FileUtils.deleteDirectory(getTestFile("target/quickstart"));

        // ----------------------------------------------------------------------
        // This needs to be encapsulated in a maven test case.
        // ----------------------------------------------------------------------

        String mavenRepoLocal =
                getTestFile("target/local-repository").toURI().toURL().getFile();

        String mavenRepoRemote =
                getTestFile("src/test/repository").toURI().toURL().toString();

        Session session = TestSessions.create(new File(mavenRepoLocal));

        List<RemoteRepository> remoteRepositories = new ArrayList<>();
        remoteRepositories.add(session.createRemoteRepository("remote", mavenRepoRemote));

        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest()
                .setPackage("org.apache.maven.quickstart")
                .setGroupId("maven")
                .setArtifactId("quickstart")
                .setVersion("1.0-alpha-1-SNAPSHOT")
                .setArchetypeGroupId("org.apache.maven.archetypes")
                .setArchetypeArtifactId("maven-archetype-quickstart")
                .setArchetypeVersion("1.0-alpha-1-SNAPSHOT")
                .setRemoteRepositories(remoteRepositories)
                .setSession(session)
                .setOutputDirectory(getTestFile("target").getAbsolutePath());

        archetype.createArchetype(request);

        // ----------------------------------------------------------------------
        // Set up the Velocity context
        // ----------------------------------------------------------------------

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("basedir", request.getOutputDirectory());
        parameters.put("package", request.getPackage());
        parameters.put("packageName", request.getPackage());
        parameters.put("groupId", request.getGroupId());
        parameters.put("artifactId", request.getArtifactId());
        parameters.put("version", request.getVersion());

        Context context = new VelocityContext();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }

        // ----------------------------------------------------------------------
        // Validate POM generation
        // ----------------------------------------------------------------------

        ArtifactCoordinates archetypeArtifact = session.createArtifactCoordinates(
                request.getArchetypeGroupId(), request.getArchetypeArtifactId(), request.getArchetypeVersion(), "jar");

        StringWriter writer = new StringWriter();

        ClassLoader old = Thread.currentThread().getContextClassLoader();

        Thread.currentThread()
                .setContextClassLoader(getContextClassloader(session, archetypeArtifact, remoteRepositories));

        try {
            velocity.getEngine()
                    .mergeTemplate(
                            OldArchetype.ARCHETYPE_RESOURCES + "/" + OldArchetype.ARCHETYPE_POM,
                            "utf-8",
                            context,
                            writer);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        Model generatedModel, templateModel;
        try {
            StringReader strReader = new StringReader(writer.toString());

            templateModel = modelXmlFactory.read(strReader);
        } catch (XmlReaderException e) {
            throw new ArchetypeTemplateProcessingException("Error reading template POM", e);
        }

        File artifactDir = getTestFile("target", (String) parameters.get("artifactId"));
        File pomFile = getTestFile(artifactDir.getAbsolutePath(), OldArchetype.ARCHETYPE_POM);

        try (FileReader pomReader = new FileReader(pomFile)) {
            generatedModel = modelXmlFactory.read(pomReader);
        } catch (IOException | XmlReaderException e) {
            throw new ArchetypeTemplateProcessingException("Error reading generated POM", e);
        }
        assertEquals(
                generatedModel.getArtifactId(),
                templateModel.getArtifactId(),
                "Generated POM ArtifactId is not equivalent to expected result.");
        assertEquals(
                generatedModel.getGroupId(),
                templateModel.getGroupId(),
                "Generated POM GroupId is not equivalent to expected result.");
        assertEquals(
                generatedModel.getId(),
                templateModel.getId(),
                "Generated POM Id is not equivalent to expected result.");
        assertEquals(
                generatedModel.getVersion(),
                templateModel.getVersion(),
                "Generated POM Version is not equivalent to expected result.");
        assertEquals(
                generatedModel.getPackaging(),
                templateModel.getPackaging(),
                "Generated POM Packaging is not equivalent to expected result.");
        assertEquals(
                generatedModel.getDevelopers(),
                templateModel.getDevelopers(),
                "Generated POM Developers is not equivalent to expected result.");
        assertEquals(
                generatedModel.getScm(),
                templateModel.getScm(),
                "Generated POM Scm is not equivalent to expected result.");
    }

    // Gets the classloader for this artifact's file.
    private ClassLoader getContextClassloader(
            Session session, ArtifactCoordinates archetypeArtifact, List<RemoteRepository> remoteRepositories)
            throws Exception {
        URL[] urls = new URL[] {
            session.resolveArtifact(archetypeArtifact, remoteRepositories)
                    .getPath()
                    .toUri()
                    .toURL()
        };
        return new URLClassLoader(urls);
    }

    @Test
    public void testAddModuleToParentPOM() throws Exception {
        String pom = "<project>\n" + "  <packaging>pom</packaging>\n" + "</project>";

        StringWriter out = new StringWriter();
        assertTrue(DefaultOldArchetype.addModuleToParentPom("myArtifactId1", new StringReader(pom), out));

        assertXmlEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<project>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>myArtifactId1</module>\n"
                        + "  </modules>\n"
                        + "</project>",
                out.toString());

        pom = "<project>\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <packaging>pom</packaging>\n"
                + "</project>";

        out = new StringWriter();
        assertTrue(DefaultOldArchetype.addModuleToParentPom("myArtifactId2", new StringReader(pom), out));

        assertXmlEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<project>\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>myArtifactId2</module>\n"
                        + "  </modules>\n"
                        + "</project>",
                out.toString());

        pom = "<project><modelVersion>4.0.0</modelVersion>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <modules>\n"
                + "  </modules>\n"
                + "</project>";

        out = new StringWriter();
        assertTrue(DefaultOldArchetype.addModuleToParentPom("myArtifactId3", new StringReader(pom), out));

        assertXmlEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<project><modelVersion>4.0.0</modelVersion>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>myArtifactId3</module>\n"
                        + "  </modules>\n"
                        + "</project>",
                out.toString());

        pom = "<project><modelVersion>4.0.0</modelVersion>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <modules>\n"
                + "    <module>myArtifactId3</module>\n"
                + "  </modules>\n"
                + "</project>";

        out = new StringWriter();
        assertTrue(DefaultOldArchetype.addModuleToParentPom("myArtifactId4", new StringReader(pom), out));

        assertXmlEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<project><modelVersion>4.0.0</modelVersion>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>myArtifactId3</module>\n"
                        + "    <module>myArtifactId4</module>\n"
                        + "  </modules>\n"
                        + "</project>",
                out.toString());

        pom = "<project><modelVersion>4.0.0</modelVersion>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <modules>\n"
                + "    <module>myArtifactId3</module>\n"
                + "  </modules>\n"
                + "</project>";

        out = new StringWriter();
        assertFalse(DefaultOldArchetype.addModuleToParentPom("myArtifactId3", new StringReader(pom), out));

        // empty means unchanged
        assertEquals("", out.toString().trim());

        pom = "<project><modelVersion>4.0.0</modelVersion>\n"
                + "  <packaging>pom</packaging>\n"
                + "  <modules>\n"
                + "    <module>myArtifactId1</module>\n"
                + "    <module>myArtifactId2</module>\n"
                + "    <module>myArtifactId3</module>\n"
                + "  </modules>\n"
                + "  <profiles>\n"
                + "    <profile>\n"
                + "      <id>profile1</id>\n"
                + "      <modules>\n"
                + "        <module>module1</module>\n"
                + "      </modules>\n"
                + "    </profile>\n"
                + "    <profile>\n"
                + "      <id>profile2</id>\n"
                + "      <modules>\n"
                + "        <module>module2</module>\n"
                + "      </modules>\n"
                + "    </profile>\n"
                + "  </profiles>\n"
                + "</project>";

        out = new StringWriter();
        assertTrue(DefaultOldArchetype.addModuleToParentPom("module1", new StringReader(pom), out));

        assertXmlEquals(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                        + "<project><modelVersion>4.0.0</modelVersion>\n"
                        + "  <packaging>pom</packaging>\n"
                        + "  <modules>\n"
                        + "    <module>myArtifactId1</module>\n"
                        + "    <module>myArtifactId2</module>\n"
                        + "    <module>myArtifactId3</module>\n"
                        + "    <module>module1</module>\n"
                        + "  </modules>\n"
                        + "  <profiles>\n"
                        + "    <profile>\n"
                        + "      <id>profile1</id>\n"
                        + "      <modules>\n"
                        + "        <module>module1</module>\n"
                        + "      </modules>\n"
                        + "    </profile>\n"
                        + "    <profile>\n"
                        + "      <id>profile2</id>\n"
                        + "      <modules>\n"
                        + "        <module>module2</module>\n"
                        + "      </modules>\n"
                        + "    </profile>\n"
                        + "  </profiles>\n"
                        + "</project>",
                out.toString());
    }

    private static void assertXmlEquals(String expectedXml, String actualXml) {
        Diff diff = DiffBuilder.compare(expectedXml)
                .withTest(actualXml)
                .normalizeWhitespace()
                .build();
        assertFalse(diff.hasDifferences(), diff::toString);
    }

    @Test
    public void testAddModuleToParentPOMNoPackaging() throws Exception {
        try {
            String pom = "<project>\n</project>";
            DefaultOldArchetype.addModuleToParentPom("myArtifactId1", new StringReader(pom), new StringWriter());
            fail("Should fail to add a module to a JAR packaged project");
        } catch (InvalidPackaging e) {
            // great!
            assertEquals(
                    "Unable to add module to the current project as it is not of packaging type 'pom'",
                    e.getLocalizedMessage());
        }
    }

    @Test
    public void testAddModuleToParentPOMJarPackaging() throws Exception {
        try {
            String pom = "<project>\n  <packaging>jar</packaging>\n</project>";
            DefaultOldArchetype.addModuleToParentPom("myArtifactId1", new StringReader(pom), new StringWriter());
            fail("Should fail to add a module to a JAR packaged project");
        } catch (InvalidPackaging e) {
            // great!
            assertEquals(
                    "Unable to add module to the current project as it is not of packaging type 'pom'",
                    e.getLocalizedMessage());
        }
    }
}
