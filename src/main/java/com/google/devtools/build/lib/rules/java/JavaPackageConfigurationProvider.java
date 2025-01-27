// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.java;

import static com.google.devtools.build.lib.rules.java.JavaStarlarkCommon.checkPrivateAccess;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.PackageSpecificationProvider;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.lib.collect.nestedset.Depset;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.packages.Info;
import com.google.devtools.build.lib.packages.PackageSpecification.PackageGroupContents;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.packages.StarlarkProviderWrapper;
import com.google.devtools.build.lib.packages.StructImpl;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

/** A provider for Java per-package configuration. */
@Immutable
public final class JavaPackageConfigurationProvider implements StarlarkValue {

  public static final StarlarkProviderWrapper<JavaPackageConfigurationProvider> PROVIDER =
      new Provider();

  private final StructImpl underlying;

  private JavaPackageConfigurationProvider(StructImpl underlying) {
    this.underlying = underlying;
  }

  /** Package specifications for which the configuration should be applied. */
  private ImmutableList<PackageSpecificationProvider> packageSpecifications()
      throws RuleErrorException {
    try {
      return Sequence.noneableCast(
              underlying.getValue("package_specs"),
              PackageSpecificationProvider.class,
              "package_specs")
          .getImmutableList();
    } catch (EvalException e) {
      throw new RuleErrorException(e);
    }
  }

  ImmutableList<String> javacoptsAsList() throws RuleErrorException {
    try {
      return Sequence.noneableCast(
              underlying.getValue("javac_opts_list"), String.class, "javac_opts_list")
          .getImmutableList();
    } catch (EvalException e) {
      throw new RuleErrorException(e);
    }
  }

  NestedSet<Artifact> data() throws RuleErrorException {
    try {
      return Depset.noneableCast(underlying.getValue("data"), Artifact.class, "data");
    } catch (EvalException e) {
      throw new RuleErrorException(e);
    }
  }

  /**
   * Returns true if this configuration matches the current label: that is, if the label's package
   * is contained by any of the {@link #packageSpecifications}.
   */
  public boolean matches(Label label) throws RuleErrorException {
    // Do not use streams here as they create excessive garbage.
    for (PackageSpecificationProvider provider : packageSpecifications()) {
      for (PackageGroupContents specifications : provider.getPackageSpecifications().toList()) {
        if (specifications.containsPackage(label.getPackageIdentifier())) {
          return true;
        }
      }
    }
    return false;
  }

  @StarlarkMethod(
      name = "matches",
      documented = false,
      parameters = {
        @Param(
            name = "label",
            allowedTypes = {@ParamType(type = Label.class)})
      },
      useStarlarkThread = true)
  public boolean starlarkMatches(Label label, StarlarkThread starlarkThread)
      throws EvalException, RuleErrorException {
    checkPrivateAccess(starlarkThread);
    return matches(label);
  }

  @StarlarkMethod(
      name = "javac_opts",
      parameters = {@Param(name = "as_depset", defaultValue = "False", named = true)},
      documented = false,
      useStarlarkThread = true)
  public Object starlarkJavacOpts(boolean asDepset, StarlarkThread starlarkThread)
      throws EvalException, InterruptedException {
    checkPrivateAccess(starlarkThread);
    return Starlark.call(
        starlarkThread,
        underlying.getValue("javac_opts"),
        ImmutableList.of(asDepset),
        ImmutableMap.of());
  }

  @StarlarkMethod(name = "data", documented = false, useStarlarkThread = true)
  public Depset starlarkData(StarlarkThread starlarkThread) throws EvalException {
    checkPrivateAccess(starlarkThread);
    return underlying.getValue("data", Depset.class);
  }

  private static class Provider extends StarlarkProviderWrapper<JavaPackageConfigurationProvider> {

    private Provider() {
      super(
          Label.parseCanonicalUnchecked("@_builtins//:common/java/java_package_configuration.bzl"),
          "JavaPackageConfigurationInfo");
    }

    @Override
    public JavaPackageConfigurationProvider wrap(Info value) throws RuleErrorException {
      if (value instanceof StructImpl) {
        return new JavaPackageConfigurationProvider((StructImpl) value);
      } else {
        throw new RuleErrorException(
            "expected an instance of JavaPackageConfigurationProvider, got: "
                + Starlark.type(value));
      }
    }
  }

}
