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
package org.apache.maven.archetype.common;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.BuildBase;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.Profile;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.api.services.xml.XmlWriterException;
import org.apache.maven.api.xml.XmlNode;
import org.apache.maven.archetype.common.util.Format;
import org.apache.maven.archetype.common.util.PomUtils;
import org.apache.maven.archetype.exception.InvalidPackaging;
import org.apache.maven.archetype.old.ArchetypeTemplateProcessingException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Named
@Singleton
public class DefaultPomManager implements PomManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPomManager.class);

    private final ModelXmlFactory modelXmlFactory;

    @Inject
    public DefaultPomManager(ModelXmlFactory modelXmlFactory) {
        this.modelXmlFactory = modelXmlFactory;
    }

    @Override
    public void addModule(File pom, String artifactId)
            throws IOException, ParserConfigurationException, TransformerException, SAXException, InvalidPackaging,
                    ArchetypeTemplateProcessingException {
        StringWriter out = new StringWriter();
        boolean found = PomUtils.addNewModule(artifactId, new XmlStreamReader(pom), out);
        if (found) {
            FileUtils.fileWrite(pom.getAbsolutePath(), out.toString());
        }
    }

    @Override
    public void addParent(File pom, File parentPom) throws IOException, XmlPullParserException {
        Model generatedModel = readPom(pom);
        if (null != generatedModel.getParent()) {
            LOGGER.info("Parent element not overwritten in " + pom);
            return;
        }

        Model parentModel = readPom(parentPom);

        Parent.Builder parent = Parent.newBuilder();
        String groupId = parentModel.getGroupId();
        if (groupId == null) {
            groupId = parentModel.getParent().getGroupId();
        }
        parent.groupId(groupId);
        parent.artifactId(parentModel.getArtifactId());
        String version = parentModel.getVersion();
        if (version == null) {
            version = parentModel.getParent().getVersion();
        }
        parent.version(version);

        writePom(generatedModel.withParent(parent.build()), pom, pom);
    }

    @Override
    public void mergePoms(File pom, File temporaryPom) throws IOException, XmlPullParserException {
        Model model = readPom(pom);
        Model generatedModel = readPom(temporaryPom);

        Model.Builder merged = Model.newBuilder(model);

        Map<String, String> properties = new LinkedHashMap<>(model.getProperties());
        properties.putAll(generatedModel.getProperties());
        merged.properties(properties);

        merged.dependencies(mergeDependencies(model.getDependencies(), generatedModel.getDependencies()));
        merged.build(mergeModelBuild(model, generatedModel));
        merged.profiles(mergeProfiles(model, generatedModel));
        merged.reporting(mergeReportPlugins(model, generatedModel));

        //
        //        // Potential merging
        //
        //        model.getModelEncoding ();
        //        model.getModelVersion ();
        //
        //        model.getGroupId ();
        //        model.getArtifactId ();
        //        model.getVersion ();
        //        model.getParent ();
        //
        //        model.getId ();
        //        model.getName ();
        //        model.getInceptionYear ();
        //        model.getDescription ();
        //        model.getUrl ();
        //        model.getLicenses ();
        //        model.getProperties ();
        //
        //        model.getOrganization ();
        //        model.getMailingLists ();
        //        model.getContributors ();
        //        model.getDevelopers ();
        //
        //        model.getScm ();
        //        model.getCiManagement ();
        //        model.getDistributionManagement ();
        //        model.getIssueManagement ();
        //
        //        model.getPackaging ();
        ////        model.getDependencies (); // done
        //        model.getDependencyManagement ();
        //        model.getPrerequisites ().getMaven ();
        //        model.getPrerequisites ().getModelEncoding ();
        //
        //        model.getProfiles ();
        //        model.getModules ();
        //        model.getRepositories ();
        //        model.getPluginRepositories ();
        //
        //        model.getBuild ().getDefaultGoal ();
        //        model.getBuild ().getFinalName ();
        //        model.getBuild ().getModelEncoding ();
        //        model.getBuild ().getFilters ();
        //        model.getBuild ().getDirectory ();
        //        model.getBuild ().getOutputDirectory ();
        //        model.getBuild ().getSourceDirectory ();
        //        model.getBuild ().getResources ();
        //        model.getBuild ().getScriptSourceDirectory ();
        //        model.getBuild ().getTestOutputDirectory ();
        //        model.getBuild ().getTestResources ();
        //        model.getBuild ().getTestSourceDirectory ();
        //        model.getBuild ().getPluginManagement ();
        //        model.getBuild ().getExtensions ();
        ////        model.getBuild ().getPluginsAsMap (); // done
        //
        //        model.getReporting ().getModelEncoding ();
        //        model.getReporting ().getOutputDirectory ();
        ////        model.getReporting ().getReportPluginsAsMap (); // done
        //

        writePom(merged.build(), pom, pom);
    }

    @Override
    public Model readPom(final File pomFile) throws IOException, XmlPullParserException {
        try (Reader pomReader = new XmlStreamReader(pomFile)) {
            return read(pomReader);
        }
    }

    @Override
    public Model readPom(InputStream pomStream) throws IOException, XmlPullParserException {
        try (Reader pomReader = new XmlStreamReader(pomStream)) {
            return read(pomReader);
        }
    }

    private Model read(Reader pomReader) throws XmlPullParserException {
        try {
            // not strict: the Maven 4 model rejects the <reports> and <goals> elements the Maven 3 model
            // accepted, and rewriting a POM keeps them as they are
            return modelXmlFactory.read(pomReader, false);
        } catch (XmlReaderException e) {
            throw new XmlPullParserException(e.getMessage());
        }
    }

    @Override
    public void writePom(final Model model, final File pomFile, final File initialPomFile) throws IOException {
        String fileEncoding = StringUtils.isEmpty(model.getModelEncoding()) ? "UTF-8" : model.getModelEncoding();

        org.jdom2.Document doc;
        try (InputStream inputStream = Files.newInputStream(initialPomFile.toPath())) {
            SAXBuilder builder = new SAXBuilder();
            doc = builder.build(inputStream);
        } catch (JDOMException exc) {
            throw new IOException(
                    "Cannot parse the POM by JDOM while reading " + initialPomFile + ": " + exc.getMessage(), exc);
        }

        if (!pomFile.exists() && !pomFile.createNewFile()) {
            LOGGER.warn("Could not create new file \"" + pomFile.getPath() + "\" or the file already exists.");
        }

        try (Writer outputStreamWriter = new OutputStreamWriter(new FileOutputStream(pomFile), fileEncoding)) {
            // The cdata parts of the pom are not preserved from initial to target
            MavenJDOMWriter writer = new MavenJDOMWriter();

            final String ls = System.lineSeparator();
            Format form = Format.getRawFormat().setEncoding(fileEncoding).setLineSeparator(ls);
            writer.write(model, doc, outputStreamWriter, form);
        } catch (FileNotFoundException e) {
            LOGGER.debug("Creating pom file " + pomFile);

            try (Writer pomWriter = new OutputStreamWriter(Files.newOutputStream(pomFile.toPath()), fileEncoding)) {
                modelXmlFactory.write(model, pomWriter);
            } catch (XmlWriterException ex) {
                throw new IOException("Cannot write the POM " + pomFile + ": " + ex.getMessage(), ex);
            }
        }
    }

    private List<Dependency> mergeDependencies(List<Dependency> dependencies, List<Dependency> generated) {
        Map<String, Dependency> dependenciesByIds = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            dependenciesByIds.put(dependency.getManagementKey(), dependency);
        }

        List<Dependency> result = new ArrayList<>(dependencies);
        for (Dependency generatedDependency : generated) {
            String generatedDependencyId = generatedDependency.getManagementKey();
            if (!dependenciesByIds.containsKey(generatedDependencyId)) {
                result.add(generatedDependency);
            } else {
                LOGGER.warn("Can not override property: " + generatedDependencyId);
            }
        }
        return result;
    }

    private Build mergeModelBuild(Model model, Model generatedModel) {
        if (generatedModel.getBuild() == null) {
            return model.getBuild();
        }

        Build build = model.getBuild() == null ? Build.newInstance(false) : model.getBuild();
        return build.withPlugins(mergeBuildPlugins(build, generatedModel.getBuild()));
    }

    private List<Profile> mergeProfiles(Model model, Model generatedModel) {
        List<Profile> generatedProfiles = generatedModel.getProfiles();
        List<Profile> result = new ArrayList<>(model.getProfiles());
        if (generatedProfiles.isEmpty()) {
            return result;
        }

        // add profile ids from the model for later lookups
        Map<String, Integer> modelProfileIdIndex = new LinkedHashMap<>();
        for (int i = 0; i < result.size(); i++) {
            modelProfileIdIndex.put(result.get(i).getId(), i);
        }

        for (Profile generatedProfile : generatedProfiles) {
            String generatedProfileId = generatedProfile.getId();
            Integer index = modelProfileIdIndex.get(generatedProfileId);
            if (index == null) {
                result.add(generatedProfile);
            } else {
                LOGGER.warn("Try to merge profiles with id " + generatedProfileId);
                result.set(index, mergeProfile(result.get(index), generatedProfile));
            }
        }
        return result;
    }

    private Profile mergeProfile(Profile modelProfile, Profile generatedProfile) {
        Profile.Builder merged = Profile.newBuilder(modelProfile);

        // ModelBase can be a Model or a Profile...
        merged.dependencies(mergeDependencies(modelProfile.getDependencies(), generatedProfile.getDependencies()));
        if (!generatedProfile.getDependencies().isEmpty()) {
            Map<String, String> properties = new LinkedHashMap<>(modelProfile.getProperties());
            properties.putAll(generatedProfile.getProperties());
            merged.properties(properties);
        }
        // TODO: maybe merge more than just dependencies and properties...

        if (generatedProfile.getBuild() != null) {
            BuildBase build = modelProfile.getBuild() == null ? BuildBase.newInstance(false) : modelProfile.getBuild();
            // TODO: merge more than just plugins in the profile...
            merged.build(build.withPlugins(mergeBuildPlugins(build, generatedProfile.getBuild())));
        }
        return merged.build();
    }

    private Reporting mergeReportPlugins(Model model, Model generatedModel) {
        if (generatedModel.getReporting() == null) {
            return model.getReporting();
        }

        Reporting reporting = model.getReporting() == null ? Reporting.newInstance(false) : model.getReporting();

        Map<String, ReportPlugin> reportPluginsByIds = new LinkedHashMap<>();
        for (ReportPlugin reportPlugin : reporting.getPlugins()) {
            reportPluginsByIds.put(reportPlugin.getKey(), reportPlugin);
        }

        List<ReportPlugin> plugins = new ArrayList<>(reporting.getPlugins());
        for (ReportPlugin generatedReportPlugin : generatedModel.getReporting().getPlugins()) {
            if (!reportPluginsByIds.containsKey(generatedReportPlugin.getKey())) {
                plugins.add(generatedReportPlugin);
            } else {
                LOGGER.warn("Can not override report: " + generatedReportPlugin.getKey());
            }
        }
        return reporting.withPlugins(plugins);
    }

    private List<Plugin> mergeBuildPlugins(BuildBase modelBuild, BuildBase generatedModelBuild) {
        List<Plugin> result = new ArrayList<>(modelBuild.getPlugins());
        Map<String, Integer> pluginIndexByIds = new LinkedHashMap<>();
        for (int i = 0; i < result.size(); i++) {
            pluginIndexByIds.put(result.get(i).getKey(), i);
        }

        for (Plugin generatedPlugin : generatedModelBuild.getPlugins()) {
            String generatedPluginsId = generatedPlugin.getKey();
            Integer index = pluginIndexByIds.get(generatedPluginsId);

            if (index == null) {
                result.add(generatedPlugin);
            } else {
                LOGGER.info("Try to merge plugin configuration of plugins with id: " + generatedPluginsId);
                Plugin modelPlugin = result.get(index);

                result.set(
                        index,
                        modelPlugin.withConfiguration(
                                XmlNode.merge(generatedPlugin.getConfiguration(), modelPlugin.getConfiguration())));
            }
        }
        return result;
    }
}
