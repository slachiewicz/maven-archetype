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
package org.apache.maven.archetype.creator;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.BuildBase;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.model.Extension;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginManagement;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.model.Resource;
import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.archetype.ArchetypeCreationRequest;
import org.apache.maven.archetype.ArchetypeCreationResult;
import org.apache.maven.archetype.common.ArchetypeFilesResolver;
import org.apache.maven.archetype.common.Constants;
import org.apache.maven.archetype.common.MavenBuilds;
import org.apache.maven.archetype.common.PomManager;
import org.apache.maven.archetype.common.util.ListScanner;
import org.apache.maven.archetype.common.util.PathUtils;
import org.apache.maven.archetype.metadata.ArchetypeDescriptor;
import org.apache.maven.archetype.metadata.FileSet;
import org.apache.maven.archetype.metadata.ModuleDescriptor;
import org.apache.maven.archetype.metadata.RequiredProperty;
import org.apache.maven.archetype.metadata.io.xpp3.ArchetypeDescriptorXpp3Writer;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.commons.io.IOUtils.write;

/**
 * Create a 2.x Archetype project from a project. Since 2.0-alpha-5, an integration-test named "basic" is created along
 * the archetype itself to provide immediate test when building the archetype.
 */
@Named("fileset")
@Singleton
public class FilesetArchetypeCreator implements ArchetypeCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilesetArchetypeCreator.class);
    private static final String DEFAULT_OUTPUT_DIRECTORY =
            "target" + File.separator + "generated-sources" + File.separator + "archetype";

    private ArchetypeFilesResolver archetypeFilesResolver;

    private PomManager pomManager;

    @Inject
    public FilesetArchetypeCreator(ArchetypeFilesResolver archetypeFilesResolver, PomManager pomManager) {
        this.archetypeFilesResolver = archetypeFilesResolver;
        this.pomManager = pomManager;
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public void createArchetype(ArchetypeCreationRequest request, ArchetypeCreationResult result) {
        Project project = request.getProject();
        List<String> languages = request.getLanguages();
        List<String> filtereds = request.getFiltereds();
        String defaultEncoding = request.getDefaultEncoding();
        boolean preserveCData = request.isPreserveCData();
        boolean keepParent = request.isKeepParent();
        boolean partialArchetype = request.isPartialArchetype();
        File outputDirectory = request.getOutputDirectory();
        File basedir = project.getBasedir().toFile();

        Properties properties = new Properties();
        Properties configurationProperties = new Properties();
        if (request.getProperties() != null) {
            properties.putAll(request.getProperties());
            configurationProperties.putAll(request.getProperties());
        }

        extractPropertiesFromProject(project, properties, configurationProperties, request.getPackageName());

        if (outputDirectory == null) {
            LOGGER.debug("No output directory defined, using default: " + DEFAULT_OUTPUT_DIRECTORY);
            outputDirectory = FileUtils.resolveFile(basedir, DEFAULT_OUTPUT_DIRECTORY);
        }
        outputDirectory.mkdirs();

        LOGGER.debug("Creating archetype in " + outputDirectory);

        try {
            File archetypePomFile = createArchetypeProjectPom(project, configurationProperties, outputDirectory);

            File archetypeResourcesDirectory = new File(outputDirectory, getTemplateOutputDirectory());

            File archetypeFilesDirectory = new File(archetypeResourcesDirectory, Constants.ARCHETYPE_RESOURCES);
            LOGGER.debug("Archetype's files output directory " + archetypeFilesDirectory);

            File archetypeDescriptorFile = new File(archetypeResourcesDirectory, Constants.ARCHETYPE_DESCRIPTOR);
            archetypeDescriptorFile.getParentFile().mkdirs();

            File archetypePostGenerationScript =
                    new File(archetypeResourcesDirectory, Constants.ARCHETYPE_POST_GENERATION_SCRIPT);
            archetypePostGenerationScript.getParentFile().mkdirs();

            if (project.getBuild() != null
                    && CollectionUtils.isNotEmpty(project.getBuild().getResources())) {
                for (Resource resource : project.getBuild().getResources()) {
                    File inputFile = new File(
                            resource.getDirectory() + File.separator + Constants.ARCHETYPE_POST_GENERATION_SCRIPT);
                    if (inputFile.exists()) {
                        FileUtils.copyFile(inputFile, archetypePostGenerationScript);
                    }
                }
            }

            LOGGER.debug("Starting archetype's descriptor " + project.getArtifactId());
            ArchetypeDescriptor archetypeDescriptor = new ArchetypeDescriptor();

            archetypeDescriptor.setName(project.getArtifactId());
            archetypeDescriptor.setPartial(partialArchetype);

            addRequiredProperties(archetypeDescriptor, properties);

            // TODO ensure reverseProperties contains NO dotted properties
            Properties reverseProperties = getReversedProperties(archetypeDescriptor, properties);
            // reverseProperties.remove( Constants.GROUP_ID );

            // TODO ensure pomReversedProperties contains NO dotted properties
            Properties pomReversedProperties = getReversedProperties(archetypeDescriptor, properties);
            // pomReversedProperties.remove( Constants.PACKAGE );

            String packageName = configurationProperties.getProperty(Constants.PACKAGE);

            Model pom = pomManager.readPom(project.getPomPath().toFile());

            List<String> excludePatterns = configurationProperties.getProperty(Constants.EXCLUDE_PATTERNS) != null
                    ? Arrays.asList(configurationProperties
                            .getProperty(Constants.EXCLUDE_PATTERNS)
                            .split(","))
                    : Collections.emptyList();

            List<String> fileNames = resolveFileNames(pom, basedir, excludePatterns);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Scanned for files " + fileNames.size());

                for (String name : fileNames) {
                    LOGGER.debug("- " + name);
                }
            }

            List<FileSet> filesets = resolveFileSets(packageName, fileNames, languages, filtereds, defaultEncoding);
            LOGGER.debug("Resolved filesets for " + archetypeDescriptor.getName());

            archetypeDescriptor.setFileSets(filesets);

            createArchetypeFiles(
                    reverseProperties,
                    filesets,
                    packageName,
                    basedir,
                    archetypeFilesDirectory,
                    defaultEncoding,
                    excludePatterns);
            LOGGER.debug("Created files for " + archetypeDescriptor.getName());

            setParentArtifactId(reverseProperties, configurationProperties.getProperty(Constants.ARTIFACT_ID));

            for (String moduleId : pom.getModules()) {
                String rootArtifactId = configurationProperties.getProperty(Constants.ARTIFACT_ID);
                String moduleIdDirectory = moduleId;

                if (moduleId.indexOf(rootArtifactId) >= 0) {
                    moduleIdDirectory = StringUtils.replace(moduleId, rootArtifactId, "__rootArtifactId__");
                }

                LOGGER.debug("Creating module " + moduleId);

                ModuleDescriptor moduleDescriptor = createModule(
                        reverseProperties,
                        rootArtifactId,
                        moduleId,
                        packageName,
                        FileUtils.resolveFile(basedir, moduleId),
                        new File(archetypeFilesDirectory, moduleIdDirectory),
                        languages,
                        filtereds,
                        defaultEncoding,
                        preserveCData,
                        keepParent);

                archetypeDescriptor.addModule(moduleDescriptor);

                LOGGER.debug("Added module " + moduleDescriptor.getName() + " in " + archetypeDescriptor.getName());
            }

            restoreParentArtifactId(reverseProperties, null);
            restoreArtifactId(reverseProperties, configurationProperties.getProperty(Constants.ARTIFACT_ID));

            createPoms(
                    pom,
                    configurationProperties.getProperty(Constants.ARTIFACT_ID),
                    configurationProperties.getProperty(Constants.ARTIFACT_ID),
                    archetypeFilesDirectory,
                    basedir,
                    project.getPomPath().toFile(),
                    pomReversedProperties,
                    preserveCData,
                    keepParent);
            LOGGER.debug("Created Archetype " + archetypeDescriptor.getName() + " template pom(s)");

            try (Writer out = new XmlStreamWriter(archetypeDescriptorFile)) {
                ArchetypeDescriptorXpp3Writer writer = new ArchetypeDescriptorXpp3Writer();

                writer.write(out, archetypeDescriptor);

                LOGGER.debug("Archetype " + archetypeDescriptor.getName() + " descriptor written");
            }

            createArchetypeBasicIt(archetypeDescriptor, outputDirectory);

            // Copy archetype integration tests.
            File archetypeIntegrationTestInputFolder =
                    new File(basedir, Constants.SRC + File.separator + "it" + File.separator + "projects");
            File archetypeIntegrationTestOutputFolder = new File(
                    outputDirectory,
                    Constants.SRC + File.separator + Constants.TEST
                            + File.separator + Constants.RESOURCES
                            + File.separator + "projects");

            if (archetypeIntegrationTestInputFolder.exists()) {
                LOGGER.info("Copying: " + archetypeIntegrationTestInputFolder.getAbsolutePath() + " into "
                        + archetypeIntegrationTestOutputFolder.getAbsolutePath());

                FileUtils.copyDirectoryStructure(
                        archetypeIntegrationTestInputFolder, archetypeIntegrationTestOutputFolder);
            }
            List<String> arguments = new ArrayList<>();
            arguments.add("-B");
            arguments.add("-f");
            arguments.add(archetypePomFile.getAbsolutePath());
            if (request.getSettingsFile() != null) {
                arguments.add("-s");
                arguments.add(request.getSettingsFile().getAbsolutePath());
            }
            if (request.getLocalRepositoryBasedir() != null) {
                arguments.add("-Dmaven.repo.local="
                        + request.getLocalRepositoryBasedir().getAbsolutePath());
            }
            String httpsProtocols = System.getProperty("https.protocols");
            if (httpsProtocols != null) {
                arguments.add("-Dhttps.protocols=" + httpsProtocols);
            }
            arguments.add(request.getPostPhase());

            ExecutorResult buildResult = MavenBuilds.run(ExecutorRequest.mavenBuilder()
                    .cwd(archetypePomFile.getAbsoluteFile().getParentFile().toPath())
                    .arguments(arguments)
                    .stdOut(MavenBuilds.keepOpen(System.out))
                    .stdErr(MavenBuilds.keepOpen(System.err))
                    .build());
            if (!buildResult.success()) {
                throw new Exception("Maven process ended with exit code "
                        + buildResult.exitCode().orElse(-1) + "!");
            }

        } catch (Exception e) {
            result.setCause(e);
        }
    }

    /**
     * Create an archetype IT, ie goals.txt and archetype.properties in src/test/resources/projects/basic.
     *
     * @param archetypeDescriptor
     * @param generatedSourcesDirectory
     * @throws IOException
     * @since 2.0-alpha-5
     */
    private void createArchetypeBasicIt(ArchetypeDescriptor archetypeDescriptor, File generatedSourcesDirectory)
            throws IOException {
        String basic = Constants.SRC + File.separator + Constants.TEST + File.separator + Constants.RESOURCES
                + File.separator + "projects" + File.separator + "basic";
        File basicItDirectory = new File(generatedSourcesDirectory, basic);
        basicItDirectory.mkdirs();

        File archetypePropertiesFile = new File(basicItDirectory, "archetype.properties");
        if (!archetypePropertiesFile.exists() && !archetypePropertiesFile.createNewFile()) {
            LOGGER.warn("Could not create new file \"" + archetypePropertiesFile.getPath()
                    + "\" or the file already exists.");
        }

        try (InputStream in = FilesetArchetypeCreator.class.getResourceAsStream("archetype.properties");
                OutputStream out = Files.newOutputStream(archetypePropertiesFile.toPath())) {
            Properties archetypeProperties = new Properties();
            archetypeProperties.load(in);

            for (RequiredProperty req : archetypeDescriptor.getRequiredProperties()) {
                archetypeProperties.put(req.getKey(), req.getDefaultValue());
            }

            archetypeProperties.store(out, null);
        }

        copyResource("goal.txt", new File(basicItDirectory, "goal.txt"));

        LOGGER.debug("Added basic integration test");
    }

    private void extractPropertiesFromProject(
            Project project, Properties properties, Properties configurationProperties, String packageName) {
        if (!properties.containsKey(Constants.GROUP_ID)) {
            properties.setProperty(Constants.GROUP_ID, project.getGroupId());
        }
        configurationProperties.setProperty(Constants.GROUP_ID, properties.getProperty(Constants.GROUP_ID));

        if (!properties.containsKey(Constants.ARTIFACT_ID)) {
            properties.setProperty(Constants.ARTIFACT_ID, project.getArtifactId());
        }
        configurationProperties.setProperty(Constants.ARTIFACT_ID, properties.getProperty(Constants.ARTIFACT_ID));

        if (!properties.containsKey(Constants.VERSION)) {
            properties.setProperty(Constants.VERSION, project.getVersion());
        }
        configurationProperties.setProperty(Constants.VERSION, properties.getProperty(Constants.VERSION));

        if (packageName != null) {
            properties.setProperty(Constants.PACKAGE, packageName);
        } else if (!properties.containsKey(Constants.PACKAGE)) {
            properties.setProperty(Constants.PACKAGE, project.getGroupId());
        }
        configurationProperties.setProperty(Constants.PACKAGE, properties.getProperty(Constants.PACKAGE));
    }

    /**
     * Create the archetype project pom.xml file, that will be used to build the archetype.
     */
    private File createArchetypeProjectPom(Project project, Properties configurationProperties, File projectDir)
            throws IOException {
        Model projectModel = project.getModel();

        Model.Builder model = Model.newBuilder(false);
        model.modelVersion("4.0.0");
        // these values should be retrieved from the request with sensible defaults
        model.groupId(configurationProperties.getProperty(Constants.ARCHETYPE_GROUP_ID, project.getGroupId()));
        model.artifactId(configurationProperties.getProperty(Constants.ARCHETYPE_ARTIFACT_ID, project.getArtifactId()));
        model.version(configurationProperties.getProperty(Constants.ARCHETYPE_VERSION, project.getVersion()));
        model.packaging("maven-archetype");
        model.name(configurationProperties.getProperty(Constants.ARCHETYPE_ARTIFACT_ID, project.getArtifactId()));
        model.url(configurationProperties.getProperty(Constants.ARCHETYPE_URL, projectModel.getUrl()));
        model.description(
                configurationProperties.getProperty(Constants.ARCHETYPE_DESCRIPTION, projectModel.getDescription()));
        model.licenses(projectModel.getLicenses());
        model.developers(projectModel.getDevelopers());
        model.scm(projectModel.getScm());
        List<Extension> extensions = new ArrayList<>();

        // In many cases where we are behind a firewall making Archetypes for work mates we want
        // to simply be able to deploy the archetypes once we have created them. In order to do
        // this we want to utilize information from the project we are creating the archetype from.
        // This will be a fully working project that has been testing and inherits from a POM
        // that contains deployment information, along with any extensions required for deployment.
        // We don't want to create archetypes that cannot be deployed after we create them. People
        // might want to edit the archetype POM but they should not have too.

        if (project.getParent().isPresent()) {
            Model parentModel = project.getParent().get().getModel();

            if (parentModel.getDistributionManagement() != null) {
                model.distributionManagement(parentModel.getDistributionManagement());
            }

            if (parentModel.getBuild() != null) {
                extensions.addAll(parentModel.getBuild().getExtensions());
            }
        }

        extensions.add(Extension.newBuilder(false)
                .groupId("org.apache.maven.archetype")
                .artifactId("archetype-packaging")
                .version(getArchetypeVersion())
                .build());
        model.build(Build.newBuilder(false).extensions(extensions).build());

        LOGGER.debug("Creating archetype's pom");

        File archetypePomFile = new File(projectDir, Constants.ARCHETYPE_POM);

        archetypePomFile.getParentFile().mkdirs();

        copyResource("pom-prototype.xml", archetypePomFile);

        pomManager.writePom(model.build(), archetypePomFile, archetypePomFile);

        return archetypePomFile;
    }

    private void copyResource(String name, File destination) throws IOException {
        if (!destination.exists() && !destination.createNewFile()) {
            LOGGER.warn("Could not create new file \"" + destination.getPath() + "\" or the file already exists.");
        }

        try (InputStream in = FilesetArchetypeCreator.class.getResourceAsStream(name);
                OutputStream out = Files.newOutputStream(destination.toPath())) {
            IOUtil.copy(in, out);
        }
    }

    private void addRequiredProperties(ArchetypeDescriptor archetypeDescriptor, Properties properties) {
        Properties requiredProperties = new Properties();
        requiredProperties.putAll(properties);
        requiredProperties.remove(Constants.ARCHETYPE_GROUP_ID);
        requiredProperties.remove(Constants.ARCHETYPE_ARTIFACT_ID);
        requiredProperties.remove(Constants.ARCHETYPE_VERSION);
        requiredProperties.remove(Constants.GROUP_ID);
        requiredProperties.remove(Constants.ARTIFACT_ID);
        requiredProperties.remove(Constants.VERSION);
        requiredProperties.remove(Constants.PACKAGE);
        requiredProperties.remove(Constants.EXCLUDE_PATTERNS);

        for (Iterator<?> propertiesIterator = requiredProperties.keySet().iterator(); propertiesIterator.hasNext(); ) {
            String propertyKey = (String) propertiesIterator.next();

            RequiredProperty requiredProperty = new RequiredProperty();
            requiredProperty.setKey(propertyKey);
            requiredProperty.setDefaultValue(requiredProperties.getProperty(propertyKey));

            archetypeDescriptor.addRequiredProperty(requiredProperty);

            LOGGER.debug("Adding requiredProperty " + propertyKey + "=" + requiredProperties.getProperty(propertyKey)
                    + " to archetype's descriptor");
        }
    }

    private void createModulePoms(
            Properties pomReversedProperties,
            String rootArtifactId,
            String packageName,
            File basedir,
            File archetypeFilesDirectory,
            boolean preserveCData,
            boolean keepParent)
            throws IOException, XmlPullParserException {
        Model pom = pomManager.readPom(FileUtils.resolveFile(basedir, Constants.ARCHETYPE_POM));

        String parentArtifactId = pomReversedProperties.getProperty(Constants.PARENT_ARTIFACT_ID);
        String artifactId = pom.getArtifactId();
        setParentArtifactId(pomReversedProperties, pomReversedProperties.getProperty(Constants.ARTIFACT_ID));
        setArtifactId(pomReversedProperties, pom.getArtifactId());

        for (String subModuleId : pom.getModules()) {
            String subModuleIdDirectory = subModuleId;

            if (subModuleId.indexOf(rootArtifactId) >= 0) {
                subModuleIdDirectory = StringUtils.replace(subModuleId, rootArtifactId, "__rootArtifactId__");
            }

            createModulePoms(
                    pomReversedProperties,
                    rootArtifactId,
                    packageName,
                    FileUtils.resolveFile(basedir, subModuleId),
                    FileUtils.resolveFile(archetypeFilesDirectory, subModuleIdDirectory),
                    preserveCData,
                    keepParent);
        }

        createModulePom(
                pom,
                rootArtifactId,
                archetypeFilesDirectory,
                pomReversedProperties,
                FileUtils.resolveFile(basedir, Constants.ARCHETYPE_POM),
                preserveCData,
                keepParent);

        restoreParentArtifactId(pomReversedProperties, parentArtifactId);
        restoreArtifactId(pomReversedProperties, artifactId);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void createPoms(
            Model pom,
            String rootArtifactId,
            String artifactId,
            File archetypeFilesDirectory,
            File basedir,
            File rootPom,
            Properties pomReversedProperties,
            boolean preserveCData,
            boolean keepParent)
            throws IOException, XmlPullParserException {
        setArtifactId(pomReversedProperties, pom.getArtifactId());

        for (String moduleId : pom.getModules()) {
            String moduleIdDirectory = moduleId;

            if (moduleId.indexOf(rootArtifactId) >= 0) {
                moduleIdDirectory = StringUtils.replace(moduleId, rootArtifactId, "__rootArtifactId__");
            }

            createModulePoms(
                    pomReversedProperties,
                    rootArtifactId,
                    moduleId,
                    FileUtils.resolveFile(basedir, moduleId),
                    new File(archetypeFilesDirectory, moduleIdDirectory),
                    preserveCData,
                    keepParent);
        }

        restoreParentArtifactId(pomReversedProperties, null);
        restoreArtifactId(pomReversedProperties, artifactId);

        createArchetypePom(pom, archetypeFilesDirectory, pomReversedProperties, rootPom, preserveCData, keepParent);
    }

    private String getPackageInPathFormat(String aPackage) {
        return StringUtils.replace(aPackage, ".", "/");
    }

    private Model rewriteReferences(Model pom, String rootArtifactId, String groupId) {
        Model.Builder rewritten = Model.newBuilder(pom);

        // rewrite Dependencies
        rewritten.dependencies(rewriteDependencies(pom.getDependencies(), rootArtifactId, groupId));

        // rewrite DependencyManagement
        rewritten.dependencyManagement(
                rewriteDependencyManagement(pom.getDependencyManagement(), rootArtifactId, groupId));

        // rewrite Plugins and PluginManagement
        if (pom.getBuild() != null) {
            rewritten.build(rewriteBuild(pom.getBuild(), rootArtifactId, groupId));
        }

        // rewrite Profiles
        List<Profile> profiles = new ArrayList<>();
        for (Profile profile : pom.getProfiles()) {
            Profile.Builder rewrittenProfile = Profile.newBuilder(profile);

            rewrittenProfile.dependencies(rewriteDependencies(profile.getDependencies(), rootArtifactId, groupId));
            rewrittenProfile.dependencyManagement(
                    rewriteDependencyManagement(profile.getDependencyManagement(), rootArtifactId, groupId));
            if (profile.getBuild() != null) {
                rewrittenProfile.build(rewriteBuild(profile.getBuild(), rootArtifactId, groupId));
            }

            profiles.add(rewrittenProfile.build());
        }
        rewritten.profiles(profiles);

        return rewritten.build();
    }

    private <T extends BuildBase> T rewriteBuild(T build, String rootArtifactId, String groupId) {
        BuildBase rewritten = build.withPlugins(rewritePlugins(build.getPlugins(), rootArtifactId, groupId));
        PluginManagement pluginManagement = build.getPluginManagement();
        if (pluginManagement != null) {
            rewritten = rewritten.withPluginManagement(pluginManagement.withPlugins(
                    rewritePlugins(pluginManagement.getPlugins(), rootArtifactId, groupId)));
        }
        @SuppressWarnings("unchecked")
        T result = (T) rewritten;
        return result;
    }

    private DependencyManagement rewriteDependencyManagement(
            DependencyManagement dependencyManagement, String rootArtifactId, String groupId) {
        if (dependencyManagement == null) {
            return null;
        }
        return dependencyManagement.withDependencies(
                rewriteDependencies(dependencyManagement.getDependencies(), rootArtifactId, groupId));
    }

    private List<Dependency> rewriteDependencies(List<Dependency> dependencies, String rootArtifactId, String groupId) {
        List<Dependency> rewritten = new ArrayList<>(dependencies.size());
        for (Dependency dependency : dependencies) {
            rewritten.add(rewriteDependencyReferences(dependency, rootArtifactId, groupId));
        }
        return rewritten;
    }

    private List<Plugin> rewritePlugins(List<Plugin> plugins, String rootArtifactId, String groupId) {
        List<Plugin> rewritten = new ArrayList<>(plugins.size());
        for (Plugin plugin : plugins) {
            rewritten.add(rewritePluginReferences(plugin, rootArtifactId, groupId));
        }
        return rewritten;
    }

    private Dependency rewriteDependencyReferences(Dependency dependency, String rootArtifactId, String groupId) {
        if (dependency.getArtifactId() != null && dependency.getArtifactId().indexOf(rootArtifactId) >= 0) {
            Dependency.Builder rewritten = Dependency.newBuilder(dependency);

            if (dependency.getGroupId() != null) {
                rewritten.groupId(
                        StringUtils.replace(dependency.getGroupId(), groupId, "${" + Constants.GROUP_ID + "}"));
            }

            rewritten.artifactId(StringUtils.replace(dependency.getArtifactId(), rootArtifactId, "${rootArtifactId}"));

            if (dependency.getVersion() != null) {
                rewritten.version("${" + Constants.VERSION + "}");
            }
            return rewritten.build();
        }
        return dependency;
    }

    private Plugin rewritePluginReferences(Plugin plugin, String rootArtifactId, String groupId) {
        if (plugin.getArtifactId() != null && plugin.getArtifactId().indexOf(rootArtifactId) >= 0) {
            Plugin.Builder rewritten = Plugin.newBuilder(plugin);

            if (plugin.getGroupId() != null) {
                rewritten.groupId(StringUtils.replace(plugin.getGroupId(), groupId, "${" + Constants.GROUP_ID + "}"));
            }

            rewritten.artifactId(StringUtils.replace(plugin.getArtifactId(), rootArtifactId, "${rootArtifactId}"));

            if (plugin.getVersion() != null) {
                rewritten.version("${" + Constants.VERSION + "}");
            }
            plugin = rewritten.build();
        }

        if (plugin.getArtifactId() != null && "maven-ear-plugin".equals(plugin.getArtifactId())) {
            plugin = rewriteEARPluginReferences(plugin, rootArtifactId, groupId);
        }
        return plugin;
    }

    private Plugin rewriteEARPluginReferences(Plugin plugin, String rootArtifactId, String groupId) {
        XmlNode configuration = plugin.getConfiguration();
        if (configuration == null || configuration.getChild("modules") == null) {
            return plugin;
        }

        XmlNode modulesConfiguration = configuration.getChild("modules");
        List<XmlNode> modules = new ArrayList<>();
        for (XmlNode module : modulesConfiguration.getChildren()) {
            modules.add(replaceValues(
                    module,
                    Map.of(
                            "groupId", value -> StringUtils.replace(value, groupId, "${" + Constants.GROUP_ID + "}"),
                            "artifactId", value -> StringUtils.replace(value, rootArtifactId, "${rootArtifactId}"),
                            "bundleFileName", value -> StringUtils.replace(value, rootArtifactId, "${rootArtifactId}"),
                            "moduleId", value -> StringUtils.replace(value, rootArtifactId, "${rootArtifactId}"),
                            "contextRoot", value -> StringUtils.replace(value, rootArtifactId, "${rootArtifactId}"))));
        }

        List<XmlNode> children = new ArrayList<>();
        boolean replaced = false;
        for (XmlNode child : configuration.getChildren()) {
            if (!replaced && "modules".equals(child.getName())) {
                children.add(copyWithChildren(child, modules));
                replaced = true;
            } else {
                children.add(child);
            }
        }
        return plugin.withConfiguration(copyWithChildren(configuration, children));
    }

    /** Replaces the value of the direct children of the node whose name is a key of the given map. */
    private XmlNode replaceValues(XmlNode node, Map<String, UnaryOperator<String>> replacements) {
        List<XmlNode> children = new ArrayList<>();
        for (XmlNode child : node.getChildren()) {
            UnaryOperator<String> replacement = replacements.get(child.getName());
            children.add(
                    replacement == null
                            ? child
                            : XmlNode.newInstance(
                                    child.getName(),
                                    replacement.apply(child.getValue()),
                                    child.getAttributes(),
                                    child.getChildren(),
                                    child.getInputLocation()));
        }
        return copyWithChildren(node, children);
    }

    private XmlNode copyWithChildren(XmlNode node, List<XmlNode> children) {
        return XmlNode.newInstance(
                node.getName(), node.getValue(), node.getAttributes(), children, node.getInputLocation());
    }

    private void setArtifactId(Properties properties, String artifactId) {
        properties.setProperty(Constants.ARTIFACT_ID, artifactId);
    }

    private List<String> concatenateToList(List<String> toConcatenate, String with) {
        List<String> result = new ArrayList<>(toConcatenate.size());

        for (String concatenate : toConcatenate) {
            result.add(((!with.isEmpty()) ? (with + "/" + concatenate) : concatenate));
        }

        return result;
    }

    private List<String> addLists(List<String> list, List<String> other) {
        List<String> result = new ArrayList<>(list.size() + other.size());
        result.addAll(list);
        result.addAll(other);
        return result;
    }

    private void copyFiles(
            File basedir,
            File archetypeFilesDirectory,
            String directory,
            List<String> fileSetResources,
            boolean packaged,
            String packageName,
            Properties reverseProperties)
            throws IOException {
        String packageAsDirectory = StringUtils.replace(packageName, ".", File.separator);

        LOGGER.debug("Package as Directory: Package:" + packageName + "->" + packageAsDirectory);

        for (String inputFileName : fileSetResources) {
            String outputFileName = packaged
                    ? StringUtils.replace(inputFileName, packageAsDirectory + File.separator, "")
                    : inputFileName;
            LOGGER.debug("InputFileName:" + inputFileName);
            LOGGER.debug("OutputFileName:" + outputFileName);

            reverseProperties.remove("archetype.languages");

            String reversedOutputFilename = getReversedFilename(outputFileName, reverseProperties);

            File outputFile = new File(archetypeFilesDirectory, reversedOutputFilename);

            File inputFile = new File(basedir, inputFileName);

            outputFile.getParentFile().mkdirs();

            FileUtils.copyFile(inputFile, outputFile);
        }
    }

    private void createArchetypeFiles(
            Properties reverseProperties,
            List<FileSet> fileSets,
            String packageName,
            File basedir,
            File archetypeFilesDirectory,
            String defaultEncoding,
            List<String> excludePatterns)
            throws IOException {
        LOGGER.debug("Creating Archetype/Module files from " + basedir + " to " + archetypeFilesDirectory);

        for (FileSet fileSet : fileSets) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(basedir);
            scanner.setIncludes(concatenateToList(fileSet.getIncludes(), fileSet.getDirectory())
                    .toArray(new String[fileSet.getIncludes().size()]));
            scanner.setExcludes(addLists(fileSet.getExcludes(), excludePatterns)
                    .toArray(new String[fileSet.getExcludes().size()]));
            scanner.addDefaultExcludes();
            LOGGER.debug("Using fileset " + fileSet);
            scanner.scan();

            List<String> fileSetResources = Arrays.asList(scanner.getIncludedFiles());
            LOGGER.debug("Scanned " + fileSetResources.size() + " resources");

            if (fileSet.isFiltered()) {
                processFileSet(
                        basedir,
                        archetypeFilesDirectory,
                        fileSet.getDirectory(),
                        fileSetResources,
                        fileSet.isPackaged(),
                        packageName,
                        reverseProperties,
                        defaultEncoding);
                LOGGER.debug("Processed " + fileSet.getDirectory() + " files");
            } else {
                copyFiles(
                        basedir,
                        archetypeFilesDirectory,
                        fileSet.getDirectory(),
                        fileSetResources,
                        fileSet.isPackaged(),
                        packageName,
                        reverseProperties);
                LOGGER.debug("Copied " + fileSet.getDirectory() + " files");
            }
        }
    }

    private void createArchetypePom(
            Model pom,
            File archetypeFilesDirectory,
            Properties pomReversedProperties,
            File initialPomFile,
            boolean preserveCData,
            boolean keepParent)
            throws IOException {
        File outputFile = FileUtils.resolveFile(archetypeFilesDirectory, Constants.ARCHETYPE_POM);

        if (preserveCData) {
            LOGGER.debug("Preserving CDATA parts of pom");
            File inputFile = FileUtils.resolveFile(archetypeFilesDirectory, Constants.ARCHETYPE_POM + ".tmp");

            FileUtils.copyFile(initialPomFile, inputFile);

            outputFile.getParentFile().mkdirs();

            try (Reader in = new XmlStreamReader(inputFile);
                    Writer out = new XmlStreamWriter(outputFile)) {
                String initialcontent = IOUtil.toString(in);

                String content = getReversedContent(initialcontent, pomReversedProperties);

                IOUtil.copy(content, out);
            }

            inputFile.delete();
        } else {
            // forceCopy: a builder based on a model cannot clear a value
            Model.Builder archetypePom = Model.newBuilder(pom, true);
            if (!keepParent) {
                archetypePom.parent(null);
            }

            archetypePom.modules(null);
            archetypePom.groupId("${" + Constants.GROUP_ID + "}");
            archetypePom.artifactId("${" + Constants.ARTIFACT_ID + "}");
            archetypePom.version("${" + Constants.VERSION + "}");
            archetypePom.name(getReversedPlainContent(pom.getName(), pomReversedProperties));
            archetypePom.description(getReversedPlainContent(pom.getDescription(), pomReversedProperties));
            archetypePom.url(getReversedPlainContent(pom.getUrl(), pomReversedProperties));

            pomManager.writePom(
                    rewriteReferences(
                            archetypePom.build(),
                            pomReversedProperties.getProperty(Constants.ARTIFACT_ID),
                            pomReversedProperties.getProperty(Constants.GROUP_ID)),
                    outputFile,
                    initialPomFile);
        }

        try (Reader in = new XmlStreamReader(initialPomFile)) {
            String initialcontent = IOUtil.toString(in);

            Iterator<?> properties = pomReversedProperties.keySet().iterator();
            while (properties.hasNext()) {
                String property = (String) properties.next();

                if (initialcontent.indexOf("${" + property + "}") > 0) {
                    LOGGER.warn("Archetype uses ${" + property + "} for internal processing, but file " + initialPomFile
                            + " contains this property already");
                }
            }
        }
    }

    private FileSet createFileSet(
            final List<String> excludes,
            final boolean packaged,
            final boolean filtered,
            final String group,
            final List<String> includes,
            String defaultEncoding) {
        FileSet fileSet = new FileSet();

        fileSet.setDirectory(group);
        fileSet.setPackaged(packaged);
        fileSet.setFiltered(filtered);
        fileSet.setIncludes(includes);
        fileSet.setExcludes(excludes);
        fileSet.setEncoding(defaultEncoding);

        LOGGER.debug("Created Fileset " + fileSet);

        return fileSet;
    }

    private List<FileSet> createFileSets(
            List<String> files,
            int level,
            boolean packaged,
            String packageName,
            boolean filtered,
            String defaultEncoding) {
        List<FileSet> fileSets = new ArrayList<>();

        if (!files.isEmpty()) {
            LOGGER.debug("Creating filesets" + (packaged ? (" packaged (" + packageName + ")") : "")
                    + (filtered ? " filtered" : "") + " at level " + level);
            if (level == 0) {
                List<String> includes = new ArrayList<>(files);
                List<String> excludes = new ArrayList<>();

                if (!includes.isEmpty()) {
                    fileSets.add(createFileSet(excludes, packaged, filtered, "", includes, defaultEncoding));
                }
            } else {
                Map<String, List<String>> groups = getGroupsMap(files, level);

                for (String group : groups.keySet()) {
                    LOGGER.debug("Creating filesets for group " + group);

                    if (!packaged) {
                        fileSets.add(getUnpackagedFileSet(filtered, group, groups.get(group), defaultEncoding));
                    } else {
                        fileSets.addAll(
                                getPackagedFileSets(filtered, group, groups.get(group), packageName, defaultEncoding));
                    }
                }
            } // end if

            LOGGER.debug("Resolved fileSets " + fileSets);
        } // end if

        return fileSets;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private ModuleDescriptor createModule(
            Properties reverseProperties,
            String rootArtifactId,
            String moduleId,
            String packageName,
            File basedir,
            File archetypeFilesDirectory,
            List<String> languages,
            List<String> filtereds,
            String defaultEncoding,
            boolean preserveCData,
            boolean keepParent)
            throws IOException, XmlPullParserException {
        ModuleDescriptor archetypeDescriptor = new ModuleDescriptor();
        LOGGER.debug("Starting module's descriptor " + moduleId);

        archetypeFilesDirectory.mkdirs();
        LOGGER.debug("Module's files output directory " + archetypeFilesDirectory);

        Model pom = pomManager.readPom(FileUtils.resolveFile(basedir, Constants.ARCHETYPE_POM));
        String replacementId = pom.getArtifactId();
        String moduleDirectory = pom.getArtifactId();

        if (replacementId.indexOf(rootArtifactId) >= 0) {
            replacementId = StringUtils.replace(replacementId, rootArtifactId, "${rootArtifactId}");
            moduleDirectory = StringUtils.replace(moduleId, rootArtifactId, "__rootArtifactId__");
        }

        if (moduleId.indexOf(rootArtifactId) >= 0) {
            moduleDirectory = StringUtils.replace(moduleId, rootArtifactId, "__rootArtifactId__");
        }

        archetypeDescriptor.setName(replacementId);
        archetypeDescriptor.setId(replacementId);
        archetypeDescriptor.setDir(moduleDirectory);

        setArtifactId(reverseProperties, pom.getArtifactId());

        List<String> excludePatterns = reverseProperties.getProperty(Constants.EXCLUDE_PATTERNS) != null
                ? Arrays.asList(StringUtils.split(reverseProperties.getProperty(Constants.EXCLUDE_PATTERNS), ","))
                : Collections.emptyList();

        List<String> fileNames = resolveFileNames(pom, basedir, excludePatterns);

        List<FileSet> filesets = resolveFileSets(packageName, fileNames, languages, filtereds, defaultEncoding);
        LOGGER.debug("Resolved filesets for module " + archetypeDescriptor.getName());

        archetypeDescriptor.setFileSets(filesets);

        createArchetypeFiles(
                reverseProperties,
                filesets,
                packageName,
                basedir,
                archetypeFilesDirectory,
                defaultEncoding,
                excludePatterns);
        LOGGER.debug("Created files for module " + archetypeDescriptor.getName());

        String parentArtifactId = reverseProperties.getProperty(Constants.PARENT_ARTIFACT_ID);
        setParentArtifactId(reverseProperties, pom.getArtifactId());

        for (String subModuleId : pom.getModules()) {
            String subModuleIdDirectory = subModuleId;
            if (subModuleId.indexOf(rootArtifactId) >= 0) {
                subModuleIdDirectory = StringUtils.replace(subModuleId, rootArtifactId, "__rootArtifactId__");
            }

            LOGGER.debug("Creating module " + subModuleId);

            ModuleDescriptor moduleDescriptor = createModule(
                    reverseProperties,
                    rootArtifactId,
                    subModuleId,
                    packageName,
                    FileUtils.resolveFile(basedir, subModuleId),
                    FileUtils.resolveFile(archetypeFilesDirectory, subModuleIdDirectory),
                    languages,
                    filtereds,
                    defaultEncoding,
                    preserveCData,
                    keepParent);

            archetypeDescriptor.addModule(moduleDescriptor);

            LOGGER.debug("Added module " + moduleDescriptor.getName() + " in " + archetypeDescriptor.getName());
        }

        restoreParentArtifactId(reverseProperties, parentArtifactId);
        restoreArtifactId(reverseProperties, pom.getArtifactId());

        LOGGER.debug("Created Module " + archetypeDescriptor.getName() + " pom");

        return archetypeDescriptor;
    }

    private void createModulePom(
            Model pom,
            String rootArtifactId,
            File archetypeFilesDirectory,
            Properties pomReversedProperties,
            File initialPomFile,
            boolean preserveCData,
            boolean keepParent)
            throws IOException {
        File outputFile = FileUtils.resolveFile(archetypeFilesDirectory, Constants.ARCHETYPE_POM);

        if (preserveCData) {
            LOGGER.debug("Preserving CDATA parts of pom");
            File inputFile = FileUtils.resolveFile(archetypeFilesDirectory, Constants.ARCHETYPE_POM + ".tmp");

            FileUtils.copyFile(initialPomFile, inputFile);

            outputFile.getParentFile().mkdirs();

            try (Reader in = new XmlStreamReader(inputFile);
                    Writer out = new XmlStreamWriter(outputFile)) {
                String initialcontent = IOUtil.toString(in);

                String content = getReversedContent(initialcontent, pomReversedProperties);

                IOUtil.copy(content, out);
            }

            inputFile.delete();
        } else {
            // forceCopy: a builder based on a model cannot clear a value
            Model.Builder modulePom = Model.newBuilder(pom, true);
            if (pom.getParent() != null) {
                Parent.Builder parent = Parent.newBuilder(pom.getParent());
                parent.groupId(StringUtils.replace(
                        pom.getParent().getGroupId(),
                        pomReversedProperties.getProperty(Constants.GROUP_ID),
                        "${" + Constants.GROUP_ID + "}"));
                if (pom.getParent().getArtifactId() != null
                        && pom.getParent().getArtifactId().indexOf(rootArtifactId) >= 0) {
                    parent.artifactId(
                            StringUtils.replace(pom.getParent().getArtifactId(), rootArtifactId, "${rootArtifactId}"));
                }
                if (pom.getParent().getVersion() != null) {
                    parent.version("${" + Constants.VERSION + "}");
                }
                modulePom.parent(parent.build());
            }
            modulePom.modules(null);

            if (pom.getGroupId() != null) {
                modulePom.groupId(StringUtils.replace(
                        pom.getGroupId(),
                        pomReversedProperties.getProperty(Constants.GROUP_ID),
                        "${" + Constants.GROUP_ID + "}"));
            }

            modulePom.artifactId("${" + Constants.ARTIFACT_ID + "}");

            if (pom.getVersion() != null) {
                modulePom.version("${" + Constants.VERSION + "}");
            }

            modulePom.name(getReversedPlainContent(pom.getName(), pomReversedProperties));
            modulePom.description(getReversedPlainContent(pom.getDescription(), pomReversedProperties));
            modulePom.url(getReversedPlainContent(pom.getUrl(), pomReversedProperties));

            pomManager.writePom(
                    rewriteReferences(
                            modulePom.build(), rootArtifactId, pomReversedProperties.getProperty(Constants.GROUP_ID)),
                    outputFile,
                    initialPomFile);
        }

        try (Reader in = new XmlStreamReader(initialPomFile)) {
            String initialcontent = IOUtil.toString(in);

            for (Iterator<?> properties = pomReversedProperties.keySet().iterator(); properties.hasNext(); ) {
                String property = (String) properties.next();

                if (initialcontent.indexOf("${" + property + "}") > 0) {
                    LOGGER.warn("OldArchetype uses ${" + property + "} for internal processing, but file "
                            + initialPomFile + " contains this property already");
                }
            }
        }
    }

    private Set<String> getExtensions(List<String> files) {
        Set<String> extensions = new HashSet<>();

        for (String file : files) {
            extensions.add(FileUtils.extension(file));
        }

        return extensions;
    }

    private Set<String> getExtensionlessFiles(List<String> files) {
        Set<String> extensionlessFiles = new HashSet<>();

        for (String file : files) {
            if (FileUtils.extension(file).isEmpty()) {
                extensionlessFiles.add(file);
            }
        }

        return extensionlessFiles;
    }

    private Map<String, List<String>> getGroupsMap(final List<String> files, final int level) {
        Map<String, List<String>> groups = new HashMap<>();

        for (String file : files) {
            String directory = PathUtils.getDirectory(file, level);
            // make all groups have unix style
            directory = StringUtils.replace(directory, File.separator, "/");

            if (!groups.containsKey(directory)) {
                groups.put(directory, new ArrayList<>());
            }

            List<String> group = groups.get(directory);

            String innerPath = file.substring(directory.length() + 1);
            // make all groups have unix style
            innerPath = StringUtils.replace(innerPath, File.separator, "/");

            group.add(innerPath);
        }

        LOGGER.debug("Sorted " + groups.size() + " groups in " + files.size() + " files");
        LOGGER.debug("Sorted Files: " + files);

        return groups;
    }

    private FileSet getPackagedFileSet(
            final boolean filtered,
            final Set<String> packagedExtensions,
            final String group,
            final Set<String> unpackagedExtensions,
            final List<String> unpackagedFiles,
            String defaultEncoding) {
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();

        for (String extension : packagedExtensions) {
            includes.add("**/*." + extension);

            if (unpackagedExtensions.contains(extension)) {
                excludes.addAll(archetypeFilesResolver.getFilesWithExtension(unpackagedFiles, extension));
            }
        }

        return createFileSet(excludes, true, filtered, group, includes, defaultEncoding);
    }

    private List<FileSet> getPackagedFileSets(
            final boolean filtered,
            final String group,
            final List<String> groupFiles,
            final String packageName,
            String defaultEncoding) {
        String packageAsDir = StringUtils.replace(packageName, ".", "/");

        List<FileSet> packagedFileSets = new ArrayList<>();
        List<String> packagedFiles = archetypeFilesResolver.getPackagedFiles(groupFiles, packageAsDir);
        LOGGER.debug("Found packaged Files:" + packagedFiles);

        List<String> unpackagedFiles = archetypeFilesResolver.getUnpackagedFiles(groupFiles, packageAsDir);
        LOGGER.debug("Found unpackaged Files:" + unpackagedFiles);

        Set<String> packagedExtensions = getExtensions(packagedFiles);
        LOGGER.debug("Found packaged extensions " + packagedExtensions);

        Set<String> unpackagedExtensions = getExtensions(unpackagedFiles);

        if (!packagedExtensions.isEmpty()) {
            packagedFileSets.add(getPackagedFileSet(
                    filtered, packagedExtensions, group, unpackagedExtensions, unpackagedFiles, defaultEncoding));
        }

        if (!unpackagedExtensions.isEmpty()) {
            LOGGER.debug("Found unpackaged extensions " + unpackagedExtensions);

            packagedFileSets.add(getUnpackagedFileSet(
                    filtered, unpackagedExtensions, unpackagedFiles, group, packagedExtensions, defaultEncoding));
        }

        return packagedFileSets;
    }

    private void setParentArtifactId(Properties properties, String parentArtifactId) {
        properties.setProperty(Constants.PARENT_ARTIFACT_ID, parentArtifactId);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void processFileSet(
            File basedir,
            File archetypeFilesDirectory,
            String directory,
            List<String> fileSetResources,
            boolean packaged,
            String packageName,
            Properties reverseProperties,
            String defaultEncoding)
            throws IOException {
        String packageAsDirectory = StringUtils.replace(packageName, ".", File.separator);

        LOGGER.debug("Package as Directory: Package:" + packageName + "->" + packageAsDirectory);

        for (String inputFileName : fileSetResources) {
            String initialFilename = packaged
                    ? StringUtils.replace(inputFileName, packageAsDirectory + File.separator, "")
                    : inputFileName;

            LOGGER.debug("InputFileName:" + inputFileName);

            File inputFile = new File(basedir, inputFileName);

            String fileEncoding = getFileCharsetEncoding(inputFile, defaultEncoding);

            String initialcontent = IOUtil.toString(Files.newInputStream(inputFile.toPath()), fileEncoding);

            for (Iterator<?> properties = reverseProperties.keySet().iterator(); properties.hasNext(); ) {
                String property = (String) properties.next();

                if (initialcontent.indexOf("${" + property + "}") > 0) {
                    LOGGER.warn("Archetype uses ${" + property + "} for internal processing, but file " + inputFile
                            + " contains this property already");
                }
            }

            String content = getReversedContent(initialcontent, reverseProperties);
            String outputFilename = getReversedFilename(initialFilename, reverseProperties);

            LOGGER.debug("OutputFileName:" + outputFilename);

            File outputFile = new File(archetypeFilesDirectory, outputFilename);
            outputFile.getParentFile().mkdirs();

            if (!outputFile.exists() && !outputFile.createNewFile()) {
                LOGGER.warn("Could not create new file \"" + outputFile.getPath() + "\" or the file already exists.");
            }

            try (OutputStream os = Files.newOutputStream(outputFile.toPath())) {
                write(content, os, fileEncoding);
            }
        }
    }

    private Properties getReversedProperties(ArchetypeDescriptor archetypeDescriptor, Properties properties) {
        Properties reversedProperties = new Properties();

        reversedProperties.putAll(properties);
        reversedProperties.remove(Constants.ARCHETYPE_GROUP_ID);
        reversedProperties.remove(Constants.ARCHETYPE_ARTIFACT_ID);
        reversedProperties.remove(Constants.ARCHETYPE_VERSION);

        String packageName = properties.getProperty(Constants.PACKAGE);
        String packageInPathFormat = getPackageInPathFormat(packageName);
        if (!packageInPathFormat.equals(packageName)) {
            reversedProperties.setProperty(Constants.PACKAGE_IN_PATH_FORMAT, packageInPathFormat);
        }

        // TODO check that reversed properties are all different and no one is a substring of another?
        // to avoid wrong variable replacements

        return reversedProperties;
    }

    private List<String> resolveFileNames(final Model pom, final File basedir, List<String> excludePatterns)
            throws IOException {
        LOGGER.debug("Resolving files for " + pom.getId() + " in " + basedir);

        StringBuilder buff = new StringBuilder("pom.xml*,archetype.properties*,target/**,");
        for (String module : pom.getModules()) {
            buff.append(',').append(module).append("/**");
        }

        for (String defaultExclude : ListScanner.DEFAULTEXCLUDES) {
            buff.append(',').append(defaultExclude).append("/**");
        }

        for (String excludePattern : excludePatterns) {
            buff.append(',').append(excludePattern);
        }

        String excludes = PathUtils.convertPathForOS(buff.toString());

        List<String> fileNames = FileUtils.getFileNames(basedir, "**,.*,**/.*", excludes, false);

        LOGGER.debug("Resolved " + fileNames.size() + " files");
        LOGGER.debug("Resolved Files:" + fileNames);

        return fileNames;
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private List<FileSet> resolveFileSets(
            String packageName,
            List<String> fileNames,
            List<String> languages,
            List<String> filtereds,
            String defaultEncoding) {
        List<FileSet> resolvedFileSets = new ArrayList<>();
        LOGGER.debug("Resolving filesets with package=" + packageName + ", languages=" + languages + " and extentions="
                + filtereds);

        List<String> files = new ArrayList<>(fileNames);

        StringBuilder languageIncludes = new StringBuilder();

        for (String language : languages) {
            languageIncludes.append(((languageIncludes.length() == 0) ? "" : ",") + language + "/**");
        }

        LOGGER.debug("Using languages includes " + languageIncludes);

        StringBuilder filteredIncludes = new StringBuilder();
        for (String filtered : filtereds) {
            filteredIncludes.append(((filteredIncludes.length() == 0) ? "" : ",") + "**/"
                    + (filtered.startsWith(".") ? "" : "*.") + filtered);
        }

        LOGGER.debug("Using filtered includes " + filteredIncludes);

        /* sourcesMainFiles */
        List<String> sourcesMainFiles = archetypeFilesResolver.findSourcesMainFiles(files, languageIncludes.toString());
        if (!sourcesMainFiles.isEmpty()) {
            files.removeAll(sourcesMainFiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(sourcesMainFiles, filteredIncludes.toString());
            sourcesMainFiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = sourcesMainFiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 3, true, packageName, true, defaultEncoding));
            }

            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 3, true, packageName, false, defaultEncoding));
            }
        }

        /* resourcesMainFiles */
        List<String> resourcesMainFiles =
                archetypeFilesResolver.findResourcesMainFiles(files, languageIncludes.toString());
        if (!resourcesMainFiles.isEmpty()) {
            files.removeAll(resourcesMainFiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(resourcesMainFiles, filteredIncludes.toString());
            resourcesMainFiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = resourcesMainFiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 3, false, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 3, false, packageName, false, defaultEncoding));
            }
        }

        /* sourcesTestFiles */
        List<String> sourcesTestFiles = archetypeFilesResolver.findSourcesTestFiles(files, languageIncludes.toString());
        if (!sourcesTestFiles.isEmpty()) {
            files.removeAll(sourcesTestFiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(sourcesTestFiles, filteredIncludes.toString());
            sourcesTestFiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = sourcesTestFiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 3, true, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 3, true, packageName, false, defaultEncoding));
            }
        }

        /* ressourcesTestFiles */
        List<String> resourcesTestFiles =
                archetypeFilesResolver.findResourcesTestFiles(files, languageIncludes.toString());
        if (!resourcesTestFiles.isEmpty()) {
            files.removeAll(resourcesTestFiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(resourcesTestFiles, filteredIncludes.toString());
            resourcesTestFiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = resourcesTestFiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 3, false, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 3, false, packageName, false, defaultEncoding));
            }
        }

        /* siteFiles */
        List<String> siteFiles = archetypeFilesResolver.findSiteFiles(files, languageIncludes.toString());
        if (!siteFiles.isEmpty()) {
            files.removeAll(siteFiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(siteFiles, filteredIncludes.toString());
            siteFiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = siteFiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 2, false, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 2, false, packageName, false, defaultEncoding));
            }
        }

        /* thirdLevelSourcesfiles */
        List<String> thirdLevelSourcesfiles =
                archetypeFilesResolver.findOtherSources(3, files, languageIncludes.toString());
        if (!thirdLevelSourcesfiles.isEmpty()) {
            files.removeAll(thirdLevelSourcesfiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(thirdLevelSourcesfiles, filteredIncludes.toString());
            thirdLevelSourcesfiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = thirdLevelSourcesfiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 3, true, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 3, true, packageName, false, defaultEncoding));
            }

            /* thirdLevelResourcesfiles */
            List<String> thirdLevelResourcesfiles = archetypeFilesResolver.findOtherResources(
                    3, files, thirdLevelSourcesfiles, languageIncludes.toString());
            if (!thirdLevelResourcesfiles.isEmpty()) {
                files.removeAll(thirdLevelResourcesfiles);
                filteredFiles =
                        archetypeFilesResolver.getFilteredFiles(thirdLevelResourcesfiles, filteredIncludes.toString());
                thirdLevelResourcesfiles.removeAll(filteredFiles);
                unfilteredFiles = thirdLevelResourcesfiles;
                if (!filteredFiles.isEmpty()) {
                    resolvedFileSets.addAll(
                            createFileSets(filteredFiles, 3, false, packageName, true, defaultEncoding));
                }
                if (!unfilteredFiles.isEmpty()) {
                    resolvedFileSets.addAll(
                            createFileSets(unfilteredFiles, 3, false, packageName, false, defaultEncoding));
                }
            }
        } // end if

        /* secondLevelSourcesfiles */
        List<String> secondLevelSourcesfiles =
                archetypeFilesResolver.findOtherSources(2, files, languageIncludes.toString());
        if (!secondLevelSourcesfiles.isEmpty()) {
            files.removeAll(secondLevelSourcesfiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(secondLevelSourcesfiles, filteredIncludes.toString());
            secondLevelSourcesfiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = secondLevelSourcesfiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 2, true, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 2, true, packageName, false, defaultEncoding));
            }
        }

        /* secondLevelResourcesfiles */
        List<String> secondLevelResourcesfiles =
                archetypeFilesResolver.findOtherResources(2, files, languageIncludes.toString());
        if (!secondLevelResourcesfiles.isEmpty()) {
            files.removeAll(secondLevelResourcesfiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(secondLevelResourcesfiles, filteredIncludes.toString());
            secondLevelResourcesfiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = secondLevelResourcesfiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 2, false, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 2, false, packageName, false, defaultEncoding));
            }
        }

        /* rootResourcesfiles */
        List<String> rootResourcesfiles =
                archetypeFilesResolver.findOtherResources(0, files, languageIncludes.toString());
        if (!rootResourcesfiles.isEmpty()) {
            files.removeAll(rootResourcesfiles);

            List<String> filteredFiles =
                    archetypeFilesResolver.getFilteredFiles(rootResourcesfiles, filteredIncludes.toString());
            rootResourcesfiles.removeAll(filteredFiles);

            List<String> unfilteredFiles = rootResourcesfiles;
            if (!filteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(filteredFiles, 0, false, packageName, true, defaultEncoding));
            }
            if (!unfilteredFiles.isEmpty()) {
                resolvedFileSets.addAll(createFileSets(unfilteredFiles, 0, false, packageName, false, defaultEncoding));
            }
        }

        if (!files.isEmpty()) {
            LOGGER.info("Ignored files: " + files);
        }

        return resolvedFileSets;
    }

    private void restoreArtifactId(Properties properties, String artifactId) {
        if (artifactId == null || artifactId.isEmpty()) {
            properties.remove(Constants.ARTIFACT_ID);
        } else {
            properties.setProperty(Constants.ARTIFACT_ID, artifactId);
        }
    }

    private void restoreParentArtifactId(Properties properties, String parentArtifactId) {
        if (parentArtifactId == null || parentArtifactId.isEmpty()) {
            properties.remove(Constants.PARENT_ARTIFACT_ID);
        } else {
            properties.setProperty(Constants.PARENT_ARTIFACT_ID, parentArtifactId);
        }
    }

    private String getReversedContent(String content, Properties properties) {
        String result =
                StringUtils.replace(StringUtils.replace(content, "$", "${symbol_dollar}"), "\\", "${symbol_escape}");
        result = getReversedPlainContent(result, properties);

        // TODO: Replace velocity to a better engine...
        return "#set( $symbol_pound = '#' )\n" + "#set( $symbol_dollar = '$' )\n" + "#set( $symbol_escape = '\\' )\n"
                + StringUtils.replace(result, "#", "${symbol_pound}");
    }

    private String getReversedPlainContent(String content, Properties properties) {
        String result = content;

        for (Iterator<?> propertyIterator = properties.keySet().iterator(); propertyIterator.hasNext(); ) {
            String propertyKey = (String) propertyIterator.next();

            result = StringUtils.replace(result, properties.getProperty(propertyKey), "${" + propertyKey + "}");
        }
        return result;
    }

    private String getReversedFilename(String filename, Properties properties) {
        String result = filename;

        for (Iterator<?> propertyIterator = properties.keySet().iterator(); propertyIterator.hasNext(); ) {
            String propertyKey = (String) propertyIterator.next();

            result = StringUtils.replace(result, properties.getProperty(propertyKey), "__" + propertyKey + "__");
        }

        return result;
    }

    private String getTemplateOutputDirectory() {
        return Constants.SRC + File.separator + Constants.MAIN + File.separator + Constants.RESOURCES;
    }

    private FileSet getUnpackagedFileSet(
            final boolean filtered, final String group, final List<String> groupFiles, String defaultEncoding) {
        Set<String> extensions = getExtensions(groupFiles);
        Set<String> extensionlessFiles = getExtensionlessFiles(groupFiles);

        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();

        for (String extension : extensions) {
            if (!extension.isEmpty()) {
                includes.add("**/*." + extension);
            }
        }
        for (String extensionlessFile : extensionlessFiles) {
            includes.add("**/" + extensionlessFile);
        }

        return createFileSet(excludes, false, filtered, group, includes, defaultEncoding);
    }

    private String getFileCharsetEncoding(File detectedFile, String defaultEncoding) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(detectedFile.toPath()))) {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(in);
            CharsetMatch match = detector.detect();
            return match.getName().toUpperCase(Locale.ENGLISH);
        } catch (IOException e) {
            return defaultEncoding;
        }
    }

    private FileSet getUnpackagedFileSet(
            final boolean filtered,
            final Set<String> unpackagedExtensions,
            final List<String> unpackagedFiles,
            final String group,
            final Set<String> packagedExtensions,
            String defaultEncoding) {
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();

        for (String extension : unpackagedExtensions) {
            if (packagedExtensions.contains(extension)) {
                includes.addAll(archetypeFilesResolver.getFilesWithExtension(unpackagedFiles, extension));
            } else {
                if (extension == null || extension.isEmpty()) {
                    includes.add("**/*");
                } else {
                    includes.add("**/*." + extension);
                }
            }
        }

        return createFileSet(excludes, false, filtered, group, includes, defaultEncoding);
    }

    private static final String MAVEN_PROPERTIES =
            "META-INF/maven/org.apache.maven.archetype/archetype-common/pom.properties";

    public String getArchetypeVersion() {
        // This should actually come from the pom.properties at testing but it's not generated and put into the JAR, it
        // happens as part of the JAR plugin which is crap as it makes testing inconsistent.
        String version = "version";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(MAVEN_PROPERTIES)) {
            Properties properties = new Properties();

            if (is != null) {
                properties.load(is);

                String property = properties.getProperty("version");

                if (property != null) {
                    return property;
                }
            }

            return version;
        } catch (IOException e) {
            return version;
        }
    }
}
