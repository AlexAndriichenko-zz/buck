/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.ExportDependencies;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;

import java.nio.file.Path;

public class JavaLibraryClasspathProvider {

  private JavaLibraryClasspathProvider() {
  }

  public static ImmutableSet<Path> getOutputClasspathJars(
      JavaLibrary javaLibraryRule,
      SourcePathResolver resolver,
      Optional<SourcePath> outputJar) {
    ImmutableSet.Builder<Path> outputClasspathBuilder =
        ImmutableSet.builder();
    Iterable<JavaLibrary> javaExportedLibraryDeps;
    if (javaLibraryRule instanceof ExportDependencies) {
      javaExportedLibraryDeps =
          getJavaLibraryDeps(((ExportDependencies) javaLibraryRule).getExportedDeps());
    } else {
      javaExportedLibraryDeps = Sets.newHashSet();
    }

    for (JavaLibrary rule : javaExportedLibraryDeps) {
      outputClasspathBuilder.addAll(rule.getOutputClasspathEntries());
    }

    if (outputJar.isPresent()) {
      outputClasspathBuilder.add(resolver.getAbsolutePath(outputJar.get()));
    }

    return outputClasspathBuilder.build();
  }

  public static ImmutableSet<JavaLibrary> getTransitiveClasspathDeps(
      JavaLibrary javaLibrary,
      Optional<SourcePath> outputJar) {
    ImmutableSet.Builder<JavaLibrary> classpathDeps = ImmutableSet.builder();

    classpathDeps.addAll(
        getClasspathDeps(
            javaLibrary.getDepsForTransitiveClasspathEntries()));

    // Only add ourselves to the classpath if there's a jar to be built.
    if (outputJar.isPresent()) {
      classpathDeps.add(javaLibrary);
    }

    // Or if there are exported dependencies, to be consistent with getTransitiveClasspathEntries.
    if (javaLibrary instanceof ExportDependencies &&
        !((ExportDependencies) javaLibrary).getExportedDeps().isEmpty()) {
      classpathDeps.add(javaLibrary);
    }

    return classpathDeps.build();
  }

  public static ImmutableSetMultimap<JavaLibrary, Path> getDeclaredClasspathEntries(
      JavaLibrary javaLibraryRule) {
    final ImmutableSetMultimap.Builder<JavaLibrary, Path> classpathEntries =
        ImmutableSetMultimap.builder();

    Iterable<JavaLibrary> javaLibraryDeps = getJavaLibraryDeps(
        javaLibraryRule.getDepsForTransitiveClasspathEntries());

    for (JavaLibrary rule : javaLibraryDeps) {
      for (Path path : rule.getOutputClasspathEntries()) {
        classpathEntries.put(rule, rule.getProjectFilesystem().resolve(path));
      }
    }
    return classpathEntries.build();
  }

  static FluentIterable<JavaLibrary> getJavaLibraryDeps(Iterable<BuildRule> deps) {
    return FluentIterable.from(deps).filter(JavaLibrary.class);
  }

  /**
   * Include the classpath entries from all JavaLibraryRules that have a direct line of lineage
   * to this rule through other JavaLibraryRules. For example, in the following dependency graph:
   *
   *        A
   *      /   \
   *     B     C
   *    / \   / \
   *    D E   F G
   *
   * If all of the nodes correspond to BuildRules that implement JavaLibraryRule except for
   * B (suppose B is a Genrule), then A's classpath will include C, F, and G, but not D and E.
   * This is because D and E are used to generate B, but do not contribute .class files to things
   * that depend on B. However, if C depended on E as well as F and G, then E would be included in
   * A's classpath.
   */
  public static ImmutableSet<JavaLibrary> getClasspathDeps(Iterable<BuildRule> deps) {
    ImmutableSet.Builder<JavaLibrary> classpathDeps = ImmutableSet.builder();
    for (BuildRule dep : deps) {
      if (dep instanceof JavaLibrary) {
        classpathDeps.addAll(((JavaLibrary) dep).getTransitiveClasspathDeps());
      }
    }
    return classpathDeps.build();
  }

  /**
   * Given libraries that may contribute classpaths, visit them and collect the classpaths.
   *
   * This is used to generate transitive classpaths from library discovered in a previous traversal.
   */
  public static ImmutableSet<Path> getClasspathsFromLibraries(Iterable<JavaLibrary> libraries) {
    ImmutableSet.Builder<Path> classpathEntries = ImmutableSet.builder();
    for (JavaLibrary library : libraries) {
      classpathEntries.addAll(library.getImmediateClasspaths());
    }
    return classpathEntries.build();
  }
}
