/**
 * Copyright © 2015, Christophe Marchand
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
package com.oxiane.xml.maven.plugin;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.MessageListener;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltTransformer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * The Mojo
 * @author ext-cmarchand
 */
@Mojo(name = "xsl-compiler", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class XslCompilerMojo extends AbstractMojo {
    
    /**
     * Location of the compiled XSL
     */
    @Parameter(defaultValue = "${project.build.directory}/classes", required = true)
    private File compileDir;

    /**
     * The compile and run classPath elements
     */
    @Parameter( defaultValue = "${project.compileClasspathElements}", readonly = true, required = true )
    private List<String> classpathElements;

    /**
     * The archive extensions (comma delimited) to look in
     */
    @Parameter( defaultValue = "jar")
    private String archiveExtensions;
    
    /**
     * Location of the XSL sources
     */
    @Parameter(defaultValue = "${basedir}/src/main/xsl", required = true)
    private File xslDir;

    private String[] extensions = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String libraries=buildLibrariesList();
        List<File> xslFiles = findAllXSLs(xslDir);
        try {
            String urlSrcBaseDir = xslDir.toURI().toURL().toString();
            Path urlSrcPath = xslDir.toPath();
            Processor processor = new Processor(new Configuration());
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltTransformer transformer = compiler.compile(new StreamSource(this.getClass().getResourceAsStream("/xslImportRewriter.xsl"))).load();
            transformer.setParameter(new QName("p_libraries"), new XdmAtomicValue(libraries));
            transformer.setParameter(new QName("p_baseUrl"), new XdmAtomicValue(urlSrcBaseDir));
            transformer.setMessageListener(new MessageListener() {

                @Override
                public void message(XdmNode xn, boolean bln, SourceLocator sl) {
                    getLog().debug(xn.toString());
                }
            });
            for(File f:xslFiles) {
                Path relative = urlSrcPath.relativize(f.toPath());
                getLog().debug(LOG_PREFIX+"relative: "+relative.toString());
                Path dest = compileDir.toPath().resolve(relative);
                getLog().debug(LOG_PREFIX+"dest: "+dest.toString());
                Serializer serializer = processor.newSerializer(Files.newOutputStream(dest, StandardOpenOption.CREATE));
                serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                transformer.setDestination(serializer);
                transformer.setSource(new StreamSource(f));
                transformer.transform();
            }
        } catch(SaxonApiException | IOException ex) {
            throw new MojoExecutionException(ex.getMessage(),ex);
        }
    }
    protected String buildLibrariesList() {
        StringBuilder sb = new StringBuilder();
        for(String s: classpathElements) {
            getLog().debug(LOG_PREFIX+s);
            if(isArchive(s)) {
                try {
                    String lName = processJarFile(s);
                    if(lName!=null) {
                        sb.append(lName).append(",");
                    }
                } catch (IOException ex) {
                    getLog().error(ex.getMessage());
                    if(getLog().isWarnEnabled()) {
                        getLog().warn(ex);
                    }
                }
            }
        }
        if(sb.length()>0) sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }
    private String processJarFile(String jarFileName) throws IOException {
        File jarFile = new File(jarFileName);
        if(!jarFile.exists()) {
            getLog().warn(LOG_PREFIX+jarFileName+" does not exists");
            return null;
        }
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            for(Enumeration<? extends ZipEntry> enumer=zipFile.entries();enumer.hasMoreElements();) {
                ZipEntry ze = enumer.nextElement();
                if(ze.getName().endsWith("pom.xml")) {
                    String middle = ze.getName().substring(15, ze.getName().length()-8);
                    int pos = middle.indexOf("/");
                    String groupId = middle.substring(0, pos);
                    String artifactId = middle.substring(pos+1);
                    getLog().debug(LOG_PREFIX+"groupId="+groupId+" artifactId="+artifactId);
                    return artifactId+":";
                }
            }
        }
        return null;
    }

    /**
     * Recursively find any files whoose name ends '.xspec'
     * under the directory xspecTestDir
     *
     * @param xslDir The directory to search for XSpec files
     *
     * @return List of XSpec files
     */
    private List<File> findAllXSLs(final File xslDir) {
        final List<File> specs = new ArrayList<>();
        if (xslDir.exists()) {
            final File[] specFiles = xslDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(final File file) {
                    return file.isFile() && file.getName().endsWith(".xsl");
                }
            });
            specs.addAll(Arrays.asList(specFiles));
            for (final File subDir : xslDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(final File file) {
                    return file.isDirectory();
                }
            })) {
                specs.addAll(findAllXSLs(subDir));
            }
        }
        return specs;
    }
    private boolean isArchive(String archiveName) {
        if(extensions==null) {
            extensions = archiveExtensions.toUpperCase().split(",");
        }
        String an = archiveName.toUpperCase();
        boolean isArchive = false;
        int count=0;
        while(!isArchive && count<extensions.length) {
            isArchive = an.endsWith(extensions[count]);
            count++;
        }
        return isArchive;
    }
    private static final transient String LOG_PREFIX = "[xslCompiler] ";

}
