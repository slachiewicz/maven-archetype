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

import java.io.File;
import java.util.Properties;

import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.common.ArchetypeArtifactManager;
import org.apache.maven.archetype.metadata.ArchetypeDescriptor;
import org.apache.maven.archetype.metadata.RequiredProperty;
import org.codehaus.plexus.testing.PlexusTest;
import org.easymock.IAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;

/**
 * Tests the ability to use variables in default fields in batch mode.
 */
@PlexusTest
public class DefaultArchetypeGenerationConfigurator2Test {

    @Inject
    private DefaultArchetypeGenerationConfigurator configurator;

    private ArchetypeGenerationQueryer queryer;
    private ArchetypeDescriptor descriptor;

    @BeforeEach
    public void setUp() throws Exception {

        descriptor = new ArchetypeDescriptor();
        RequiredProperty groupId = new RequiredProperty();
        groupId.setKey("groupId");
        groupId.setDefaultValue("com.example.${groupName}");
        RequiredProperty artifactId = new RequiredProperty();
        artifactId.setKey("artifactId");
        artifactId.setDefaultValue("${serviceName}");
        RequiredProperty thePackage = new RequiredProperty();
        thePackage.setKey("package");
        thePackage.setDefaultValue("com.example.${groupName}");
        RequiredProperty groupName = new RequiredProperty();
        groupName.setKey("groupName");
        groupName.setDefaultValue(null);
        RequiredProperty serviceName = new RequiredProperty();
        serviceName.setKey("serviceName");
        serviceName.setDefaultValue(null);
        descriptor.addRequiredProperty(groupId);
        descriptor.addRequiredProperty(artifactId);
        descriptor.addRequiredProperty(thePackage);
        descriptor.addRequiredProperty(groupName);
        descriptor.addRequiredProperty(serviceName);

        ArchetypeArtifactManager manager = Mockito.mock(ArchetypeArtifactManager.class);

        File archetype = new File("archetype.jar");

        Mockito.when(manager.exists(
                        eq("archetypeGroupId"),
                        eq("archetypeArtifactId"),
                        eq("archetypeVersion"),
                        any(),
                        any()))
                .thenReturn(true);
        Mockito.when(manager.getArchetypeFile(
                        eq("archetypeGroupId"),
                        eq("archetypeArtifactId"),
                        eq("archetypeVersion"),
                        any(),
                        any()))
                .thenReturn(archetype);
        Mockito.when(manager.isFileSetArchetype(archetype)).thenReturn(true);
        Mockito.when(manager.isOldArchetype(archetype)).thenReturn(false);
        Mockito.when(manager.getFileSetArchetypeDescriptor(archetype)).thenReturn(descriptor);
        configurator.setArchetypeArtifactManager(manager);

        queryer = Mockito.mock(ArchetypeGenerationQueryer.class);
        configurator.setArchetypeGenerationQueryer(queryer);
    }

    @Test
    public void testJIRA509FileSetArchetypeDefaultsWithVariables() throws Exception {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeGroupId("archetypeGroupId");
        request.setArchetypeArtifactId("archetypeArtifactId");
        request.setArchetypeVersion("archetypeVersion");
        Properties properties = new Properties();
        properties.setProperty("groupName", "myGroupName");
        properties.setProperty("serviceName", "myServiceName");

        configurator.configureArchetype(request, Boolean.FALSE, properties);

        assertEquals("com.example.myGroupName", request.getGroupId());
        assertEquals("myServiceName", request.getArtifactId());
        assertEquals("1.0-SNAPSHOT", request.getVersion());
        assertEquals("com.example.myGroupName", request.getPackage());
    }

    @Test
    public void testInteractive() throws Exception {
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeGroupId("archetypeGroupId");
        request.setArchetypeArtifactId("archetypeArtifactId");
        request.setArchetypeVersion("archetypeVersion");
        Properties properties = new Properties();

        Mockito.when(queryer.getPropertyValue(eq("groupName"), anyString(), isNull()))
                .thenReturn("myGroupName");

        Mockito.when(queryer.getPropertyValue(eq("serviceName"), anyString(), isNull()))
                .thenReturn("myServiceName");

        Mockito.when(queryer.getPropertyValue(anyString(), anyString(), any()))
                .thenAnswer(new IAnswer<String>() {

                    @Override
                    public String answer() throws Throwable {
                        return (String) Mockito.getCurrentArguments()[1];
                    }
                });

        Mockito.when(queryer.confirmConfiguration(any())).thenReturn(Boolean.TRUE);
        configurator.configureArchetype(request, Boolean.TRUE, properties);

        assertEquals("com.example.myGroupName", request.getGroupId());
        assertEquals("myServiceName", request.getArtifactId());
        assertEquals("1.0-SNAPSHOT", request.getVersion());
        assertEquals("com.example.myGroupName", request.getPackage());
    }

    @Test
    public void testArchetype406ComplexCustomPropertyValue() throws Exception {
        RequiredProperty custom = new RequiredProperty();
        custom.setKey("serviceUpper");
        custom.setDefaultValue("${serviceName.toUpperCase()}");
        descriptor.addRequiredProperty(custom);

        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeGroupId("archetypeGroupId");
        request.setArchetypeArtifactId("archetypeArtifactId");
        request.setArchetypeVersion("archetypeVersion");
        Properties properties = new Properties();

        Mockito.when(queryer.getPropertyValue(eq("groupName"), anyString(), isNull()))
                .thenReturn("myGroupName");

        Mockito.when(queryer.getPropertyValue(eq("serviceName"), anyString(), isNull()))
                .thenReturn("myServiceName");

        Mockito.when(queryer.getPropertyValue(anyString(), anyString(), any()))
                .thenAnswer(new IAnswer<String>() {

                    @Override
                    public String answer() throws Throwable {
                        return (String) Mockito.getCurrentArguments()[1];
                    }
                });

        Mockito.when(queryer.confirmConfiguration(any())).thenReturn(Boolean.TRUE);
        configurator.configureArchetype(request, Boolean.TRUE, properties);

        assertEquals("MYSERVICENAME", request.getProperties().get("serviceUpper"));
    }

    @Test
    public void testArchetype618() throws Exception {
        RequiredProperty custom = getRequiredProperty("serviceName");
        custom.setKey("camelArtifact");
        custom.setDefaultValue(
                "${artifactId.class.forName('org.codehaus.plexus.util.StringUtils').capitaliseAllWords($artifactId.replaceAll('[^A-Za-z_\\$0-9]', ' ').replaceFirst('^(\\d)', '_$1').replaceAll('\\d', '$0 ').replaceAll('[A-Z](?=[^A-Z])', ' $0').toLowerCase()).replaceAll('\\s', '')}");
        descriptor.addRequiredProperty(custom);

        getRequiredProperty("artifactId").setDefaultValue(null);

        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest();
        request.setArchetypeGroupId("archetypeGroupId");
        request.setArchetypeArtifactId("archetypeArtifactId");
        request.setArchetypeVersion("archetypeVersion");
        Properties properties = new Properties();

        Mockito.when(queryer.getPropertyValue(eq("groupName"), anyString(), isNull()))
                .thenReturn("myGroupName");

        Mockito.when(queryer.getPropertyValue(eq("artifactId"), anyString(), isNull()))
                .thenReturn("my-service-name");

        Mockito.when(queryer.getPropertyValue(anyString(), anyString(), any()))
                .thenAnswer(new IAnswer<String>() {

                    @Override
                    public String answer() throws Throwable {
                        return (String) Mockito.getCurrentArguments()[1];
                    }
                });

        Mockito.when(queryer.confirmConfiguration(any())).thenReturn(Boolean.TRUE);
        configurator.configureArchetype(request, Boolean.TRUE, properties);

        assertEquals("MyServiceName", request.getProperties().get("camelArtifact"));
    }

    private RequiredProperty getRequiredProperty(String propertyName) {
        if (propertyName != null) {
            for (RequiredProperty candidate : descriptor.getRequiredProperties()) {
                if (propertyName.equals(candidate.getKey())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
