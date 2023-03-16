/**
 * Copyright © 2017, Christophe Marchand
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the <organization> nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package top.marchand.xml.maven.plugin.xsl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.xml.sax.InputSource;
import top.marchand.maven.saxon.utils.SaxonOptions;
import top.marchand.xml.maven.plugin.xsl.scandir.ScanListener;

/**
 * The Mojo
 *
 * @author <a href="mailto:christophe@marchand.top">Christophe Marchand</a>
 */
@Mojo(name = "xsl-compiler",
    defaultPhase = LifecyclePhase.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class XslCompilerMojo extends AbstractCompiler {
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Override
  public MavenProject getProject() {
    return project;
  }

  @Component(hint = "default")
  private DependencyGraphBuilder dependencyGraphBuilder;

  @Override
  public DependencyGraphBuilder getGraphBuilder() {
    return dependencyGraphBuilder;
  }

  /**
   * The directory containing generated classes of the project being tested.
   * This will be included after the test classes in the test classpath.
   */
  @Parameter(defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  /**
   * The filesets where to look for xsl files
   * Each fileset has a {@code dir}, and zero-to-many {@code include}, and
   * zero-to-many {@code excludes}.
   */
  @Parameter
  private List<FileSet> filesets;

  /**
   * The catalog file to use when compiling XSL.
   */
  @Parameter
  protected File catalog;

  @Parameter(defaultValue = "${project.basedir}")
  private File projectBaseDir;

  /**
   * If set to true, excluded files are logged.
   */
  @Parameter
  private boolean logExcludedFiles;

  /**
   * Saxon options. See {@linkplain  https://github.com/cmarchand/saxonOptions-mvn-plug-utils/wiki}
   */
  @Parameter
  SaxonOptions saxonOptions;

  public static final String ERROR_MESSAGE = "<filesets>\n\t<fileset>\n\t\t<dir>src/main/xsl...</dir>\n\t</fileset>\n</filesets>\n is required in xslCompiler-maven-plugin configuration";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    Log log = getLog();
    try {
      initSaxon();
    } catch (XPathException ex) {
      getLog().error("while configuring Saxon:", ex);
    }
    Path targetDir = classesDirectory.toPath();
    boolean hasError = false;
    if (filesets == null) {
      getLog().error(LOG_PREFIX + "\n" + ERROR_MESSAGE);
      throw new MojoExecutionException(ERROR_MESSAGE);
    }
    ScanListener listener = null;
    if (logExcludedFiles) {
      listener = new ScanListener() {
        @Override
        public void scanning(File dir) {
        }

        @Override
        public void fileAccepted(Path rel) {
        }

        @Override
        public void fileRejected(Path rel) {
          getLog().warn(rel.toString() + " has been excluded. If it is a resource, this resource may nto be processed as a resource, and will be probably miss in final delivery.");
        }
      };
    }
    for (FileSet fs : filesets) {
      if (fs.getUri() != null) {
        try {
          String sPath = fs.getUriPath();
          javax.xml.transform.Source source = compiler.getURIResolver().resolve(fs.getUri(), null);
          getLog().debug("source systemId=" + source.getSystemId());
          Path targetPath = targetDir.resolve(sPath);
          String sourceFileName = sPath.substring(sPath.lastIndexOf("/") + 1);
          if (sourceFileName.contains("?")) {
            sourceFileName = sourceFileName.substring(0, sourceFileName.indexOf("?") - 1);
          }
          getLog().debug(LOG_PREFIX + " sourceFileName=" + sourceFileName);
          String targetFileName = FilenameUtils.getBaseName(sourceFileName).concat(".sef");
          getLog().debug(LOG_PREFIX + " targetFileName=" + targetFileName);
          File targetFile = targetPath.getParent().resolve(targetFileName).toFile();
          compileFile(source, targetFile);
        } catch (IOException | SaxonApiException | TransformerException ex) {
          hasError = true;
          getLog().error(LOG_PREFIX + " while compiling " + fs.getUri(), ex);
        }
      } else {
        Path basedir = new File(fs.getDir()).toPath();
        for (Path p : fs.getFiles(projectBaseDir, log, listener)) {
          try {
            File sourceFile = basedir.resolve(p).toFile();
            SAXSource source = new SAXSource(new InputSource(new FileInputStream(sourceFile)));
            source.setSystemId(sourceFile.toURI().toString());
            getLog().debug("source systemId 2: " + source.getSystemId());
            Path targetPath = p.getParent() == null ? targetDir : targetDir.resolve(p.getParent());
            String sourceFileName = sourceFile.getName();
            getLog().debug(LOG_PREFIX + " sourceFileName=" + sourceFileName);
            String targetFileName = FilenameUtils.getBaseName(sourceFileName).concat(".sef");
            getLog().debug(LOG_PREFIX + " targetFileName=" + targetFileName);
            File targetFile = targetPath.resolve(targetFileName).toFile();
            try {
              compileFile(source, targetFile);
            } catch (SaxonApiException | FileNotFoundException ex) {
              hasError = true;
              getLog().error(LOG_PREFIX + " While compiling " + p, ex);
            }
          } catch (FileNotFoundException ex) {
            // should never happen, file has been previously found
            hasError = true;
            getLog().error(LOG_PREFIX + " While compiling " + p, ex);
          }
        }
      }
    }
    if (hasError) {
      throw new MojoExecutionException("Error occured while compiling Xslts. See previous log.");
    }
  }

  private static final transient String LOG_PREFIX = "[xslCompiler] ";
  private static final transient String URI_REGEX =
      "((([A-Za-z])[A-Za-z0-9+\\-\\.]*):((//(((([A-Za-z0-9\\-\\._~!$&'()*+,;=:]|(%[0-9A-Fa-f][0-9A-Fa-f]))*@))?" +
      "((\\[(((((([0-9A-Fa-f]){0,4}:)){6}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})" +
      "|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])" +
      "|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(::(((" +
      "[0-9A-Fa-f]){0,4}:)){5}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])" +
      "|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|" +
      "(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|((([0-9A-Fa-f]){0,4})?:" +
      ":((([0-9A-Fa-f]){0,4}:)){4}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4]" +
      "[0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})" +
      "|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)" +
      ")?([0-9A-Fa-f]){0,4}))?::((([0-9A-Fa-f]){0,4}:)){3}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|" +
      "(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9]" +
      "[0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|" +
      "(((((([0-9A-Fa-f]){0,4}:)){0,2}([0-9A-Fa-f]){0,4}))?::((([0-9A-Fa-f]){0,4}:)){2}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]" +
      "){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4]" +
      "[0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9])" +
      "{2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,3}([0-9A-Fa-f]){0,4}))?::([0-9A-Fa-f]){0,4}:" +
      "((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|" +
      "([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))" +
      "\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,4}([0-9A-Fa-f])" +
      "{0,4}))?::((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\." +
      "([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|" +
      "(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,5}" +
      "([0-9A-Fa-f]){0,4}))?::([0-9A-Fa-f]){0,4})|(((((([0-9A-Fa-f]){0,4}:)){0,6}([0-9A-Fa-f]){0,4}))?::))|(v([0-9A-Fa-f])" +
      "+\\.(([A-Za-z0-9\\-\\._~]|[!$&'()*+,;=]|:))+))\\])|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\." +
      "([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|" +
      "(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))|(([A-Za-z0-9\\-\\._~]|" +
      "(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]))*)((:([0-9])*))?)((/(([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])" +
      "|[!$&'()*+,;=]|:|@))*))*)|(/(((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))+((/(" +
      "([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))*))*))?)|((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])" +
      "|[!$&'()*+,;=]|:|@))+((/(([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))*))*)|)" +
      "((\\?((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@)|/|\\?))*))?((#((([A-Za-z0-9\\-\\._~]|" +
      "(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@)|/|\\?))*))?)";

  @Override
  public File getCatalogFile() {
    return catalog;
  }

  @Override
  public SaxonOptions getSaxonOptions() {
    return saxonOptions;
  }

}
