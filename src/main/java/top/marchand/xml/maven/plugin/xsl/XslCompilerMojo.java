/**
 * Copyright © 2017, Christophe Marchand
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
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
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.List;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * The Mojo
 *
 * @author <a href="mailto:christophe@marchand.top">Christophe Marchand</a>
 */
@Mojo(name = "xsl-compiler", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class XslCompilerMojo extends AbstractCompiler {

    /**
     * The directory containing generated classes of the project being tested. 
     * This will be included after the test classes in the test classpath.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}" )
    private File classesDirectory;

    /**
     * The filesets where to look for xsl files
     * Each fileset has a <tt>dir</tt>, and zero-to-many <tt>include</tt>, and 
     * zero-to-many <tt>excludes</tt>.
     */
    @Parameter
    private List<FileSet> filesets;
    
    @Parameter
    protected File catalog;
    
    public static final String ERROR_MESSAGE = "<filesets>\n\t<fileset>\n\t\t<dir>src/main/xsl...</dir>\n\t</fileset>\n</filesets>\n is required in xslCompiler-maven-plugin configuration";
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        initSaxon();
        Path targetDir = classesDirectory.toPath();
        boolean hasError = false;
        if(filesets==null) {
            getLog().error(LOG_PREFIX+"\n"+ERROR_MESSAGE);
            throw new MojoExecutionException(ERROR_MESSAGE);
        }
        for(FileSet fs: filesets) {
            Path basedir = new File(fs.getDir()).toPath();
            for(Path p: fs.getFiles(log)) {
                File sourceFile = basedir.resolve(p).toFile();
                Path targetPath = p.getParent()==null ? targetDir : targetDir.resolve(p.getParent());
                String sourceFileName = sourceFile.getName();
                getLog().debug(LOG_PREFIX+" sourceFileName="+sourceFileName);
                String targetFileName = FilenameUtils.getBaseName(sourceFileName).concat(".sef");
                getLog().debug(LOG_PREFIX+" targetFileName="+targetFileName);
                File targetFile = targetPath.resolve(targetFileName).toFile();
                try {
                    compileFile(sourceFile, targetFile);
                } catch (SaxonApiException | FileNotFoundException ex) {
                    hasError = true;
                    getLog().error(LOG_PREFIX+" While compiling "+p, ex);
                }
            }
        }
        if(hasError) {
            throw new MojoExecutionException("Error occured while compiling Xslts. See previous log.");
        }
    }

    private static final transient String LOG_PREFIX = "[xslCompiler] ";
    private static final transient String URI_REGEX = 
            "((([A-Za-z])[A-Za-z0-9+\\-\\.]*):((//(((([A-Za-z0-9\\-\\._~!$&'()*+,;=:]|(%[0-9A-Fa-f][0-9A-Fa-f]))*@))?"+
            "((\\[(((((([0-9A-Fa-f]){0,4}:)){6}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})"+
            "|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])"+
            "|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(::((("+
            "[0-9A-Fa-f]){0,4}:)){5}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])"+
            "|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|"+
            "(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|((([0-9A-Fa-f]){0,4})?:"+
            ":((([0-9A-Fa-f]){0,4}:)){4}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4]"+
            "[0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})"+
            "|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)"+
            ")?([0-9A-Fa-f]){0,4}))?::((([0-9A-Fa-f]){0,4}:)){3}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|"+
            "(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9]"+
            "[0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|"+
            "(((((([0-9A-Fa-f]){0,4}:)){0,2}([0-9A-Fa-f]){0,4}))?::((([0-9A-Fa-f]){0,4}:)){2}((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]"+
            "){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4]"+
            "[0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9])"+
            "{2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,3}([0-9A-Fa-f]){0,4}))?::([0-9A-Fa-f]){0,4}:"+
            "((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|"+
            "([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))"+
            "\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,4}([0-9A-Fa-f])"+
            "{0,4}))?::((([0-9A-Fa-f]){0,4}:([0-9A-Fa-f]){0,4})|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\."+
            "([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|"+
            "(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))))|(((((([0-9A-Fa-f]){0,4}:)){0,5}"+
            "([0-9A-Fa-f]){0,4}))?::([0-9A-Fa-f]){0,4})|(((((([0-9A-Fa-f]){0,4}:)){0,6}([0-9A-Fa-f]){0,4}))?::))|(v([0-9A-Fa-f])"+
            "+\\.(([A-Za-z0-9\\-\\._~]|[!$&'()*+,;=]|:))+))\\])|(([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\."+
            "([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|"+
            "(25[0-5]))\\.([0-9]|([1-9][0-9])|(1([0-9]){2})|(2[0-4][0-9])|(25[0-5])))|(([A-Za-z0-9\\-\\._~]|"+
            "(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]))*)((:([0-9])*))?)((/(([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])"+
            "|[!$&'()*+,;=]|:|@))*))*)|(/(((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))+((/("+
            "([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))*))*))?)|((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])"+
            "|[!$&'()*+,;=]|:|@))+((/(([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@))*))*)|)"+
            "((\\?((([A-Za-z0-9\\-\\._~]|(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@)|/|\\?))*))?((#((([A-Za-z0-9\\-\\._~]|"+
            "(%[0-9A-Fa-f][0-9A-Fa-f])|[!$&'()*+,;=]|:|@)|/|\\?))*))?)";

    @Override
    public File getCatalogFile() {
        return catalog;
    }

}
