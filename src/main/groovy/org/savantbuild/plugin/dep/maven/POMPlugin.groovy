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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.savantbuild.domain.Project
import org.savantbuild.output.Output
import org.savantbuild.plugin.groovy.BaseGroovyPlugin
import org.savantbuild.runtime.RuntimeConfiguration
import org.w3c.dom.Document
import org.w3c.dom.Element

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

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

    String text
    if (Files.exists(pomFile)) {
      output.infoln("Updating the project pom.xml file")
      byte[] bytes = Files.readAllBytes(pomFile)
      text = new String(bytes, StandardCharsets.UTF_8)
    } else {
      output.infoln("Creating the project pom.xml file")
      text = """<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
</project>"""
    }

    Document document = DOMBuilder.newInstance().parseText(text)
    Element root = document.documentElement

    // Remove the existing dependencies
    use (DOMCategory) {
      DOMCategory.setGlobalKeepIgnorableWhitespace(true)

      Element dependencies = root.dependencies.find()
      if (dependencies != null) {
        dependencies.children().each { dep -> dependencies.removeChild(dep) }
      }

      // Remove the existing licenses
      Element licenses = root.licenses.find()
      if (licenses != null) {
        licenses.children().each { lic -> licenses.removeChild(lic) }
      }

      // Add the dependencies
      updatePOM(root)
    }

    // Call the closure if it exists
    if (closure) {
      closure.call(root)
    }

    // Write out the POM file
    String result = root as String
    result = result.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    output.debugln("New pom.xml is\n\n%s", result)
    pomFile.toFile().write(result)

    output.infoln("Update complete")
  }

  private void updatePOM(Element pom) {
    Element dependencies = pom.dependencies.find()
    if (dependencies == null) {
      dependencies = pom.appendNode("dependencies")
    }

    project.dependencies.groups.values().each { group ->
      group.dependencies.each { dep ->
        Element dependency = dependencies.appendNode("dependency")
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
          Element exclusions = dependency.appendNode("exclusions")
          dep.exclusions.each { exclude ->
            Element exclusion = exclusions.appendNode("exclusion")
            setElement(exclusion, "groupId", exclude.group)
            setElement(exclusion, "artifactId", exclude.name)
          }
        }
      }
    }

    Element licenses = pom.licenses.find()
    if (licenses == null) {
      licenses = pom.appendNode("licenses")
    }

    project.licenses.each { lic ->
      Element license = licenses.appendNode("license")
      setElement(license, "name", lic.identifier)
      setElement(license, "url", lic.seeAlso.size() > 0 ? lic.seeAlso.get(0) : null)
      setElement(license, "distribution", "repo")
    }

    setElement(pom, "groupId", project.group)
    setElement(pom, "artifactId", project.name)
    setElement(pom, "version", project.version.toString())
  }

  private static void setElement(Element root, String nodeName, String value) {
    if (value == null) {
      return
    }

    Element node = root.get(nodeName).find()
    if (node == null) {
      node = root.appendNode(nodeName)
    }
    node.setValue(value)
  }
}
