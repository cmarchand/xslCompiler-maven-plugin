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
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltPackage;
import org.apache.maven.plugin.AbstractMojo;
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
 * @author ext-cmarchand
 */
@Mojo(name = "xsl-compiler", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class XslCompilerMojo extends AbstractMojo {

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
    
    private XsltCompiler compiler;
    private DocumentBuilder builder;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        initSaxon();
        Path targetDir = classesDirectory.toPath();
        boolean hasError = false;
        for(FileSet fs: filesets) {
            Path basedir = new File(fs.getDir()).toPath();
            for(Path p: fs.getFiles(log)) {
                File sourceFile = basedir.resolve(p).toFile();
                File targetFile = targetDir.resolve(p).toFile();
                try {
                    compileFile(sourceFile, targetFile);
                } catch (SaxonApiException | FileNotFoundException ex) {
                    hasError = true;
                    getLog().error("While compiling "+p, ex);
                }
            }
        }
        if(hasError) {
            throw new MojoExecutionException("Error occured while compiling Xslts. See previous log.");
        }
    }
    
    private void compileFile(final File sourceFile, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XdmNode document = builder.build(sourceFile);
        XdmNode documentRoot = (XdmNode)document.axisIterator(Axis.CHILD).next();
        if(documentRoot.getNodeName().getLocalName().equals("package")) {
            compilePackage(documentRoot, targetFile);
        } else {
            compileModule(documentRoot, targetFile);
        }
    }
    private void compilePackage(final XdmNode document, final File targetFile) throws SaxonApiException {
        XsltPackage pack = compiler.compilePackage(document.asSource());
        pack.save(targetFile);
        compiler.importPackage(pack);
    }
    private void compileModule(final XdmNode document, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XsltExecutable exec = compiler.compile(document.asSource());
        targetFile.getParentFile().mkdirs();
        exec.export(new FileOutputStream(targetFile));
    }
    private void initSaxon() {
        Configuration config = Configuration.newConfiguration();
        Processor proc = new Processor(config);
        compiler = proc.newXsltCompiler();
        builder = proc.newDocumentBuilder();
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

}
