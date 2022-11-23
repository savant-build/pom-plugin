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

import org.savantbuild.domain.Project
import org.savantbuild.output.Output
import org.savantbuild.plugin.groovy.BaseGroovyPlugin
import org.savantbuild.runtime.RuntimeConfiguration

import groovy.xml.XmlNodePrinter
import groovy.xml.XmlParser

/**
 * Maven POM plugin.
 *
 * @author Brian Pontarelli
 */
class POMPlugin extends BaseGroovyPlugin {
  public POMSettings settings = new POMSettings()

  POMPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
  }

  /**
   * Updates the project's pom.xml file using its dependencies. The optional closure is invoked and passed the Groovy
   * {@link Node} instance for the root element of the pom.xml file.
   *
   * @param closure The closure.
   */
  void update(Closure closure = null) {
    Path pomFile = project.directory.resolve("pom.xml")
    if (Files.isDirectory(pomFile) || !Files.isReadable(pomFile) || !Files.isWritable(pomFile)) {
      fail("Maven pom.xml is not readable, writable, or is a directory (🤷)")
    }

    Node root
    if (Files.exists(pomFile)) {
      output.infoln("Updating the project pom.xml file")
      root = new XmlParser().parse(pomFile.toFile())
    } else {
      output.infoln("Updating the project pom.xml file")
      root = new Node(null, "project",
          [
              "xmlns": "http://maven.apache.org/POM/4.0.0",
              "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
              "xsi:schemaLocation": "http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd"
          ]
      )
    }

    // Remove the existing dependencies
    Node dependencies = root.dependencies.find()
    if (dependencies != null) {
      dependencies.children().clear()
    }

    // Remove the existing licenses
    Node licenses = root.licenses.find()
    if (licenses != null) {
      licenses.children().clear()
    }

    // Add the dependencies
    updatePOM(root)

    // Call the closure if it exists
    if (closure) {
      closure.call(root)
    }

    // Write out the .iml file
    StringWriter writer = new StringWriter()
    XmlNodePrinter printer = new XmlNodePrinter(new PrintWriter(writer), "  ")
    printer.setPreserveWhitespace(true)
    printer.print(root)

    String result = writer.toString().trim()
    output.debugln("New pom.xml is\n\n%s", result)
    pomFile.toFile().write(result)

    output.infoln("Update complete")
  }

  private void updatePOM(Node pom) {
    Node dependencies = pom.dependencies.find()
    if (dependencies == null) {
      dependencies = pom.appendNode("dependencies")
    }

    project.dependencies.groups.values().each { group ->
      group.dependencies.each { dep ->
        Node dependency = dependencies.appendNode("dependency")
        setElement(dependency, "groupId", dep.id.group)
        setElement(dependency, "artifactId", dep.id.name)
        setElement(dependency, "version", dep.version.toString())
        setElement(dependency, "type", dep.id.type)

        def options = settings.groupToScope[group.name]
        if (options == null) {
          options = ["scope": group.name, "optional": group.name.endsWith("-optional")]
        }

        setElement(dependency, "scope", options.scope)
        setElement(dependency, "optional", (String) options.optional)

        if (dep.exclusions.size() > 0) {
          Node exclusions = dependency.appendNode("exclusions")
          dep.exclusions.each { exclude ->
            Node exclusion = exclusions.appendNode("exclusion")
            setElement(exclusion, "groupId", exclude.group)
            setElement(exclusion, "artifactId", exclude.name)
          }
        }
      }
    }

    Node licenses = pom.licenses.find()
    if (licenses == null) {
      licenses = pom.appendNode("licenses")
    }

    project.licenses.each { lic ->
      Node license = licenses.appendNode("license")
      setElement(license, "name", lic.identifier)
      setElement(license, "url", lic.seeAlso.size() > 0 ? lic.seeAlso.get(0) : null)
      setElement(license, "distribution", "repo")
    }

    setElement(pom, "groupId", project.group)
    setElement(pom, "artifactId", project.name)
    setElement(pom, "version", project.version.toString())
  }

  private static void setElement(Node root, String nodeName, String value) {
    if (value == null) {
      return
    }

    Node node = root.get(nodeName).find()
    if (node == null) {
      node = root.appendNode(nodeName)
    }
    node.setValue(value)
  }
}
