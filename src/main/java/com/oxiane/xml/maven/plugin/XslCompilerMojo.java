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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltTransformer;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.io.input.XmlStreamReader;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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
     * Location of the compiled XSL
     */
    @Parameter(defaultValue = "${project.build.directory}/classes", required = true)
    private File compileDir;

    /**
     * The compile and run classPath elements
     */
    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> classpathElements;

    /**
     * The archive extensions (comma delimited) to look in
     */
    @Parameter(defaultValue = "jar")
    private String archiveExtensions;

    /**
     * Location of the XSL sources
     */
    @Parameter(required = true)
    private com.oxiane.xml.maven.plugin.Source[] sources;

    @Parameter
    private PostCompiler[] compilers;

    private String[] extensions = null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Pattern libraries = buildLibrariesList();
        // creates a hash-structured for post-compilers
        HashMap<File, List<File>> compilersToApply = new HashMap<>();
        if (compilers != null) {
            for (PostCompiler pc : compilers) {
                File compiler = pc.getCompiler();
                for (File source : pc.getSources()) {
                    List<File> localCompilers = compilersToApply.get(source);
                    if (localCompilers == null) {
                        localCompilers = new LinkedList<>();
                        compilersToApply.put(source, localCompilers);
                    }
                    localCompilers.add(compiler);
                }
            }
        }
        for(Source source:sources) {
            List<File> xslFiles = findAllFiles(source);
            try {
                Path urlSrcPath = source.getDir().toPath();
                Processor processor = new Processor(Configuration.newConfiguration());
                XsltCompiler compiler = processor.newXsltCompiler();
                Pattern uriPattern = Pattern.compile(URI_REGEX);
                for (File f : xslFiles) {
                    Path relative = urlSrcPath.relativize(f.toPath());
                    Path dest = compileDir.toPath().resolve(relative);
                    getLog().debug(LOG_PREFIX + "file: "+f.getName()+" relative: " + relative.toString() + " dest: " + dest.toString());
                    // on crée le répertoire...
                    dest.getParent().toFile().mkdirs();
                    List<File> postCompilers = compilersToApply.get(f);
                    XmlStreamReader reader = new XmlStreamReader(f);
                    try (BufferedReader br = new BufferedReader(reader)) {
                        String line = br.readLine();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(baos));
                        while(line!=null) {
                            if(!"()".equals(libraries.pattern())) {
//                                getLog().info("Il y a des dépendances : ##"+libraries.pattern()+"##");
                                Matcher l = libraries.matcher(line);
                                if(l.find()) {
                                    for(int i=1;i<=l.groupCount();i++) {
                                        if(i==1) {
                                            bw.append(line.substring(0,l.start(i)));
                                        } else {
                                            bw.append(line.substring(l.end(i-1),l.start(i)));
                                        }
                                        String substring = line.substring(l.start(i), i==l.groupCount()?line.length():l.end(i));
                                        getLog().debug("Processing "+substring);
                                        Matcher m = uriPattern.matcher(substring);
                                        if(m.find()) {
                                            MatchResult mr = m.toMatchResult();
                                            getLog().debug("uri is "+mr.group());
                                            String uri = mr.group();
                                            String relativeUri = relative.toString();
                                            int depth = relativeUri.split("/").length;
                                            String[] values = new String[depth];
                                            for(int k=0;k<depth-1;k++) {
                                                values[k]="..";
                                            }
                                            values[depth-1]=StringUtils.substringAfter(substring, ":/");
                                            String newUri = StringUtils.join(values, "/");
                                            getLog().debug("newUri is "+newUri);
                                            bw.append(newUri);
                                            bw.append(substring.substring(uri.length()));
                                        } else {
                                            bw.append(substring);
                                        }
                                    }
                                } else {
                                    bw.append(line);
                                }
                                bw.newLine();
                            } else {
                                // cas où il n'y a pas de dépendance, il n'y a rien à modifier
//                                getLog().info("Pas de dépendance, on réécrit directement la ligne");
                                bw.append(line);
                                bw.newLine();
                            }
                            line = br.readLine();
                        }
                        bw.flush();
                        if (postCompilers != null) {
                            getLog().debug(f.getName()+" has postCompilers");
                            XsltTransformer current = null;
                            XsltTransformer start = null;
                            for (File pc : postCompilers) {
                                getLog().info("adding post-compiler " + pc.getName() + " for " + f.getName());
                                XsltTransformer comp = compiler.compile(new StreamSource(pc)).load();
                                if(current!=null) {
                                    current.setDestination(comp);
                                }
                                current = comp;
                                if(start==null) start=comp;
                            }
                            Serializer serializer = processor.newSerializer(Files.newOutputStream(dest, StandardOpenOption.CREATE));
                            serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                            current.setDestination(serializer);
                            start.setSource(new StreamSource(new ByteArrayInputStream(baos.toByteArray())));
                            start.transform();
                        } else {
                            getLog().debug("direct write to "+dest.toString());
                            FileOutputStream fos = new FileOutputStream(dest.toFile());
                            fos.write(baos.toByteArray());
                            fos.flush();
                            fos.close();
                        }
                        bw.close();
                    }
                }
            } catch (SaxonApiException | IOException ex) {
                throw new MojoExecutionException(ex.getMessage(), ex);
            }
        }
    }

    protected Pattern buildLibrariesList() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (String s : classpathElements) {
            getLog().debug(LOG_PREFIX + s);
            if (isArchive(s)) {
                try {
                    String lName = processJarFile(s);
                    if (lName != null) {
                        sb.append(lName).append("|");
                    }
                } catch (IOException ex) {
                    getLog().error(ex.getMessage());
                    if (getLog().isWarnEnabled()) {
                        getLog().warn(ex);
                    }
                }
            }
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(")");
        Pattern ret = Pattern.compile(sb.toString());
//        getLog().info("Libraries pattern: "+ret.toString());
        return ret;
    }

    private String processJarFile(String jarFileName) throws IOException {
        File jarFile = new File(jarFileName);
        if (!jarFile.exists()) {
            getLog().warn(LOG_PREFIX + jarFileName + " does not exists");
            return null;
        }
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            for (Enumeration<? extends ZipEntry> enumer = zipFile.entries(); enumer.hasMoreElements();) {
                ZipEntry ze = enumer.nextElement();
                if (ze.getName().endsWith("pom.xml")) {
                    String middle = ze.getName().substring(15, ze.getName().length() - 8);
                    int pos = middle.indexOf("/");
                    String groupId = middle.substring(0, pos);
                    String artifactId = middle.substring(pos + 1);
                    getLog().debug(LOG_PREFIX + "groupId=" + groupId + " artifactId=" + artifactId);
                    return artifactId + ":";
                }
            }
        }
        return null;
    }

    /**
     * Recursively find any files whoose name ends '.xsl' under the directory
     * xsltDir
     *
     * @param xslDir The directory to search for XSpec files
     *
     * @return List of files
     */
    private List<File> findAllFiles(final Source source) {
        final List<File> specs = new ArrayList<>();
        if (source.getDir().exists()) {
            final File[] specFiles = source.getDir().listFiles((FileFilter)new WildcardFileFilter(source.getFilespecs()));
            specs.addAll(Arrays.asList(specFiles));
            if(source.isRecurse()) {
                for (final File subDir : source.getDir().listFiles((FileFilter)DirectoryFileFilter.INSTANCE)) {
                    specs.addAll(findAllFiles(source,subDir));
                }
            }
        }
        return specs;
    }
    private List<File> findAllFiles(final Source source,File dir) {
        final List<File> specs = new ArrayList<>();
        if (dir.exists()) {
            final File[] specFiles = dir.listFiles((FileFilter)new WildcardFileFilter(source.getFilespecs()));
            specs.addAll(Arrays.asList(specFiles));
            if(source.isRecurse()) {
                for (final File subDir : dir.listFiles((FileFilter)DirectoryFileFilter.INSTANCE)) {
                    specs.addAll(findAllFiles(source,subDir));
                }
            }
        }
        return specs;
    }

    private boolean isArchive(String archiveName) {
        if (extensions == null) {
            extensions = archiveExtensions.toUpperCase().split(",");
        }
        String an = archiveName.toUpperCase();
        boolean isArchive = false;
        int count = 0;
        while (!isArchive && count < extensions.length) {
            isArchive = an.endsWith(extensions[count]);
            count++;
        }
        return isArchive;
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
