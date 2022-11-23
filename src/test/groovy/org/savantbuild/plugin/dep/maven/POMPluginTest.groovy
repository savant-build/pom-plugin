/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.dep.maven

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.dep.domain.Dependencies
import org.savantbuild.dep.domain.DependencyGroup
import org.savantbuild.dep.domain.License
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.domain.Project
import org.savantbuild.domain.Version
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.savantbuild.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals

/**
 * Tests the POM plugin.
 *
 * @author Brian Pontarelli
 */
class POMPluginTest {
  public static Path projectDir

  Output output

  Project project

  POMPlugin plugin

  Path cacheDir

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("../pom-plugin")
  }

  @BeforeMethod
  void beforeMethod() {
    FileTools.prune(projectDir.resolve("build/test"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    def path = projectDir.resolve("build/test")
    Files.createDirectories(path)
    project = new Project(path, output)
    project.group = "org.savantbuild.test"
    project.name = "pom-plugin-test"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.savantbuild.test:multiple-versions:1.0.0"),
            new Artifact("org.savantbuild.test:multiple-versions-different-dependencies:2.0.0"),
            new Artifact("org.savantbuild.test:exclusions:2.0.0", null, false, [
                new ArtifactID("org.savantbuild.test:excluded1"),
                new ArtifactID("org.savantbuild.test:excluded2")
            ])
        ),
        new DependencyGroup("runtime", true,
            new Artifact("org.savantbuild.test:intermediate:3.0.0")
        ),
        new DependencyGroup("compile-optional", true,
            new Artifact("org.savantbuild.test:optional:4.0.0")
        ),
        new DependencyGroup("provided", true,
            new Artifact("org.savantbuild.test:provided:5.0.0")
        ),
        new DependencyGroup("test-compile", false,
            new Artifact("org.savantbuild.test:test-compile:6.0.0")
        ),
        new DependencyGroup("test-runtime", false,
            new Artifact("org.savantbuild.test:test-runtime:7.0.0")
        )
    )

    cacheDir = projectDir.resolve("../savant-dependency-management/test-deps/savant")
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString())
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString())
        ),
        output
    )

    plugin = new POMPlugin(project, new RuntimeConfiguration(), output)
  }

  @Test
  void pom() throws Exception {
    def pomFile = projectDir.resolve("build/test/pom.xml")
    Files.copy(projectDir.resolve("src/test/resources/pom.xml"), pomFile)

    plugin.update()

    String actual = new String(Files.readAllBytes(pomFile))
    String expected = new String(Files.readAllBytes(projectDir.resolve("src/test/resources/pom-expected.xml")))
    assertEquals(actual, expected)
  }
}
