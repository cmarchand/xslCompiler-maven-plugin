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

import com.google.common.base.Joiner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltPackage;
import net.sf.saxon.trans.XPathException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.xml.sax.ext.EntityResolver2;
import org.xmlresolver.Catalog;
import org.xmlresolver.Resolver;
import top.marchand.maven.saxon.utils.SaxonOptions;
import top.marchand.maven.saxon.utils.SaxonUtils;
import top.marchand.xml.maven.plugin.xsl.parsers.XcSAXParserFactory;

/**
 * Ancestor class with all required code to compile a XSL
 *
 * @author <a href="mailto:christophe@marchand.top">Christophe Marchand</a>
 */
public abstract class AbstractCompiler extends AbstractMojo {
    protected DocumentBuilder builder;
    protected XsltCompiler compiler;
    private static final String LOG_PREFIX = "[AbstractXslCompiler] ";
    private List<URL> addedToSaxonJars;
    
    public abstract DependencyGraphBuilder getGraphBuilder();

    public abstract MavenProject getProject();
    /**
     * The catalog file to use. It may retruns null.
     * @return The catalog file to use.
     */
    public abstract File getCatalogFile();

    /**
     * Compiles a <tt>sourceFile</tt> to a <tt>targetFile</tt>.
     * If the file is a <tt>&lt;package&gt;</tt>, {@link #compilePackage(net.sf.saxon.s9api.XdmNode, java.io.File) } is called,
     * else {@link #compileModule(net.sf.saxon.s9api.XdmNode, java.io.File) } is called.
     * @param source The source file to compile
     * @param targetFile The target file to generate
     * @throws SaxonApiException In case of failure
     * @throws FileNotFoundException In case of failure
     */
    protected void compileFile(final javax.xml.transform.Source source, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XdmNode document = builder.build(source);
        XdmNode documentRoot = (XdmNode) document.axisIterator(Axis.CHILD).next();
        if (documentRoot.getNodeName().getLocalName().equals("package")) {
            compilePackage(documentRoot, targetFile);
        } else {
            compileModule(documentRoot, targetFile);
        }
    }

    /**
     * Compiles a standard XSL module.
     * @param document The source document
     * @param targetFile The file to generate
     * @throws SaxonApiException In case of failure
     * @throws FileNotFoundException In case of failure
     */
    protected void compileModule(final XdmNode document, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XsltExecutable exec = compiler.compile(document.asSource());
        targetFile.getParentFile().mkdirs();
        exec.export(new FileOutputStream(targetFile));
    }

    /**
     * Compiles a package, and adds to to the compiler.
     * @param document The source document
     * @param targetFile The file to generate
     * @throws SaxonApiException In case of failure
     */
    protected void compilePackage(final XdmNode document, final File targetFile) throws SaxonApiException {
        XsltPackage pack = compiler.compilePackage(document.asSource());
        pack.save(targetFile);
        compiler.importPackage(pack);
    }

    /**
     * Initialize Saxon configuration
     * @throws net.sf.saxon.trans.XPathException In case of problem
     */
    protected void initSaxon() throws XPathException {
        Configuration config = Configuration.newConfiguration();
        addedToSaxonJars = new ArrayList<>();
        config.setSourceParserClass(XcSAXParserFactory.class.getName());
        Processor proc = new Processor(config);
        SaxonUtils.prepareSaxonConfiguration(proc,getSaxonOptions());
        if(getSaxonOptions()!=null ) {
            compiler.setRelocatable("on".equals(getSaxonOptions().getRelocate()));
        }
        Resolver uriResolver;
        if(getCatalogFile()!=null) {
            getLog().debug(LOG_PREFIX+"Setting catalog to "+getCatalogFile().toURI().toString());
            // uriResolver.getCatalog().addSource(new CatalogSource.UriCatalogSource(getCatalogFile().toURI().toString()));
            uriResolver = new Resolver(new Catalog(getCatalogFile().toURI().toString()));
        } else {
            uriResolver = new Resolver();
        }
        config.setURIResolver(uriResolver);
        builder = proc.newDocumentBuilder();
        // load extension functions
        final URLClassLoader saxonClassLoader = (URLClassLoader)(config.getClass().getClassLoader());
        try {
            XPathCompiler xpCompiler = proc.newXPathCompiler();
            XPathExecutable xpExec = xpCompiler.compile("/gaulois-services/saxon/extensions/function");
            final List<String> classpath = getProject().getCompileClasspathElements();
            DependencyNode rootNode = getGraphBuilder().buildDependencyGraph(getProject(), new ArtifactFilter() {
                @Override
                public boolean include(Artifact artfct) {
                    return true;
                }
            });
            rootNode.accept(new DependencyNodeVisitor() {
                @Override
                public boolean visit(DependencyNode dn) {
                    try {
                        return processDependency(dn, classpath, saxonClassLoader);
                    } catch(OverConstrainedVersionException ex) {
                        getLog().error(LOG_PREFIX+"while processing dependency "+dn.toNodeString(), ex);
                        return false;
                    }
                }
                @Override
                public boolean endVisit(DependencyNode dn) {
                    return true;
                }
            });
            for(Enumeration<URL> enumer = saxonClassLoader.findResources("META-INF/services/top.marchand.xml.gaulois.xml"); enumer.hasMoreElements();) {
                URL serviceUrl = enumer.nextElement();
                getLog().debug(LOG_PREFIX+"loading service "+serviceUrl.toExternalForm());
                XdmNode document = builder.build(new StreamSource(serviceUrl.openStream()));
                XPathSelector selector = xpExec.load();
                selector.setContextItem(document);
                XdmSequenceIterator it = selector.evaluate().iterator();
                while(it.hasNext()) {
                    String className = it.next().getStringValue();
                    try {
                        Class clazz = saxonClassLoader.loadClass(className);
                        if(extendsClass(clazz, ExtensionFunctionDefinition.class)) {
                            Class<ExtensionFunctionDefinition> cle = (Class<ExtensionFunctionDefinition>)clazz;
                            config.registerExtensionFunction(cle.newInstance());
                            getLog().debug(LOG_PREFIX+className+"registered as Saxon extension function");
                        } else {
                            getLog().warn(LOG_PREFIX+className+" does not extends "+ExtensionFunctionDefinition.class.getName());
                        }
                    } catch(ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                        getLog().warn(LOG_PREFIX+"unable to load extension function "+className);
                    }
                }
            }
        } catch(IOException | SaxonApiException | DependencyResolutionRequiredException | DependencyGraphBuilderException ex) {
            getLog().error(LOG_PREFIX+"while looking for resources in /META-INF/services/top.marchand.xml.gaulois/", ex);
        }

        compiler = proc.newXsltCompiler();
        // https://saxonica.plan.io/issues/3835
        compiler.setJustInTimeCompilation(false);
    }
    
    private boolean processDependency(DependencyNode dn, List<String> classpath, URLClassLoader saxonClassLoader) throws OverConstrainedVersionException {
        String artifactPath = constructArtifactPath(dn.getArtifact());
//        getLog().debug(LOG_PREFIX+"artifactPath="+artifactPath);
        String jarFileName = getJarFileName(artifactPath, classpath);
//        getLog().debug(LOG_PREFIX+"jarFileName="+jarFileName);
        if(jarFileName!=null && jarFileName.endsWith(".jar")) {
            // only jar files may contain extension function libraries
            try {
                URL jarUrl = new File(jarFileName).toURI().toURL();
                getLog().debug(LOG_PREFIX+"url="+jarUrl.toExternalForm());
                URL[] urls = new URL[] {jarUrl};
                // look if parent has been added. If yes, add children
                if(dn.getParent()!=null) {
                    DependencyNode parent = dn.getParent();
                    String parentArtifactPath = constructArtifactPath(parent.getArtifact());
                    String parentJarFileName = getJarFileName(parentArtifactPath, classpath);
                    if(parentJarFileName!=null) {
                        URL jarParentUrl = new File(parentJarFileName).toURI().toURL();
                        if(addedToSaxonJars.contains(jarParentUrl)) {
                            addJarToClassLoader(jarUrl, saxonClassLoader);
                            return true;
                        }
                    }
                }
                URLClassLoader ucl = new URLClassLoader(urls);
                Enumeration<URL> enumer = ucl.findResources("META-INF/services/top.marchand.xml.gaulois.xml");
                if(enumer.hasMoreElements()) {
                    addJarToClassLoader(jarUrl, saxonClassLoader);
                    return true;
//                } else {
//                    getLog().debug(LOG_PREFIX+"no service found in "+jarUrl.toExternalForm());
                }
                return true;
            } catch(IOException ex) {
                getLog().error("while processing dependency "+dn.toNodeString()+" with jarFileName "+jarFileName, ex);
                return false;
            }
            
        }
        return true;
    }
    private String getJarFileName(String artifactPath, List<String> classpath) {
        String jarFileName = null;
        for(String s:classpath) {
            if(s.contains(artifactPath)) {
                jarFileName = s;
                break;
            }
        }
        return jarFileName;
    }
    private String constructArtifactPath(Artifact art) throws OverConstrainedVersionException {
        String groups[] = art.getGroupId().split("\\.");
//        getLog().debug(LOG_PREFIX+"groups="+Arrays.toString(groups));
        String artifacts[] = art.getArtifactId().split("\\.");
//        getLog().debug(LOG_PREFIX+"artifacts="+Arrays.toString(artifacts));
        String[] elements = new String[groups.length + artifacts.length + 1];
        System.arraycopy(groups, 0, elements, 0, groups.length);
        System.arraycopy(artifacts, 0, elements, groups.length, artifacts.length);
//        getLog().debug(LOG_PREFIX+"artifact.baseVersion="+art.getBaseVersion());
        elements[elements.length-1] = art.getBaseVersion();
        return Joiner.on(File.separator).skipNulls().join(elements);
    }
    /**
     * Returns the Processor the plugin uses.
     * @return The processor used
     */
    protected Processor getProcessor() { return compiler.getProcessor(); }
    
    /**
     * Because we may need a compiler elsewhere
     * @return The XSL compiler used
     */
    protected XsltCompiler getXsltCompiler() { return compiler; }
    
    /**
     * Because we may need a URIResolver elsewhere
     * @return  The URI resolver used
     */
    protected URIResolver getUriResolver() { return compiler.getURIResolver(); }
    
    /**
     * Returns the EntityResolver to use
     * @return The EntityResolver
     */
    protected EntityResolver2 getEntityResolver() { return (EntityResolver2)getUriResolver(); }
    
    /**
     * Because we may need a DocumentBuilder elsewhere !
     * @return The document builder
     */
    protected DocumentBuilder getBuilder() { return builder; }

    /**
     * Returns the SaxonOptions associated to this plugin
     * @return Saxon optiones
     */
    public abstract SaxonOptions getSaxonOptions();

    private boolean extendsClass(Class toCheck, Class inheritor) {
        if(toCheck.equals(inheritor)) return true;
        if(toCheck.equals(Object.class)) return false;
        return extendsClass(toCheck.getSuperclass(), inheritor);
    }
    
    protected void addJarToClassLoader(URL url, URLClassLoader ucl) {
        try {
            Method meth = ucl.getClass().getMethod("addURL", URL.class);
            meth.invoke(ucl, url);
            addedToSaxonJars.add(url);
            getLog().debug(LOG_PREFIX+url.toExternalForm()+" added to saxon classpath");
        } catch (SecurityException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            getLog().error("while adding "+url.toString()+" to classloader", ex);
        }
    }
}
