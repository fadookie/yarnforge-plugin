/*
 * Copyright 2020 Ramid Khan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ramidzkh.yarnforge;

import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.minecraftforge.gradle.common.util.*;
import net.minecraftforge.gradle.mcp.MCPRepo;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.options.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class BaseRemappingTask extends DefaultTask {

    private String version;
    private String mappings;
    protected Supplier<File> srg;

    @Option(description = "Minecraft version", option = "version")
    public void setVersion(String version) {
        this.version = version;
    }

    @Option(description = "Mappings", option = "mappings")
    public void setMappings(String mappings) {
        this.mappings = mappings;
    }

    public void setSrgProvider(Supplier<File> srg) {
        this.srg = srg;
    }

    protected MappingSet createMcpToYarn() throws IOException {
        Project project = getProject();
        String names;

        MinecraftExtension extension = project.getExtensions().findByType(MinecraftExtension.class);

        if (extension != null) {
            names = extension.getMappings();
        } else {
            names = project.project(":clean").getExtensions().findByType(MinecraftExtension.class).getMappings();
        }

        MappingSet obfToMcp = MappingBridge.mergeMcpNames(MappingBridge.loadMappingFile(MappingSet.create(), MappingFile.load(srg.get())), McpNames.load(findNames(names)));
        MappingSet obfToYarn = MappingBridge.loadTiny(MappingSet.create(), loadTree(project, mappings), "official", "named");
        return obfToMcp.reverse().merge(obfToYarn);
    }

    private File findNames(String mapping) {
        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = MCPRepo.getMappingDep(channel, version);

        return MavenArtifactDownloader.generate(getProject(), desc, false);
    }

    public TinyTree loadTree(Project project, String mappings) throws IOException {
        try (FileSystem archive = FileSystems.newFileSystem(project.getConfigurations().detachedConfiguration(project.getDependencies().create(mappings)).getSingleFile().toPath(), null)) {
            Path copy = Files.createTempFile("mappings", ".tiny");
            Path output = Files.createTempFile("mappings", ".tiny");

            Files.deleteIfExists(copy);
            Files.deleteIfExists(output);

            Files.copy(archive.getPath("mappings/mappings.tiny"), copy);

            String[] args = {
                    MinecraftRepo.create(project).getArtifact(Artifact.from("net.minecraft:client:" + version)).optionallyCache(null).asFile().getAbsolutePath(),
                    copy.toAbsolutePath().toString(),
                    output.toAbsolutePath().toString()
            };

            new CommandProposeFieldNames().run(args);

            try (BufferedReader reader = Files.newBufferedReader(output)) {
                return TinyMappingFactory.loadWithDetection(reader);
            } finally {
                Files.delete(copy);
                Files.delete(output);
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}