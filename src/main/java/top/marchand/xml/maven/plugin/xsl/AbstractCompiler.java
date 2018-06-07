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
import javax.xml.transform.URIResolver;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltPackage;
import net.sf.saxon.trans.XPathException;
import org.apache.maven.plugin.AbstractMojo;
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
        config.setSourceParserClass(XcSAXParserFactory.class.getName());
        Processor proc = new Processor(config);
        SaxonUtils.prepareSaxonConfiguration(proc,getSaxonOptions());
        if(getSaxonOptions()!=null ) {
            compiler.setRelocatable("on".equals(getSaxonOptions().getRelocate()));
        }
        Resolver uriResolver;
        if(getCatalogFile()!=null) {
            getLog().debug("Setting catalog to "+getCatalogFile().toURI().toString());
            // uriResolver.getCatalog().addSource(new CatalogSource.UriCatalogSource(getCatalogFile().toURI().toString()));
            uriResolver = new Resolver(new Catalog(getCatalogFile().toURI().toString()));
        } else {
            uriResolver = new Resolver();
        }
        config.setURIResolver(uriResolver);
        compiler = proc.newXsltCompiler();
        builder = proc.newDocumentBuilder();
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
}
