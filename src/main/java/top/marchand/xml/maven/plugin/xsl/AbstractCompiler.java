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
import org.xmlresolver.CatalogSource;
import org.xmlresolver.Resolver;

/**
 * Ancestor class with all required code to compile a XSL
 *
 * @author <a href="mailto:christophe@marchand.top">Christophe Marchand</a>
 */
public abstract class AbstractCompiler extends AbstractMojo {
    protected DocumentBuilder builder;
    protected XsltCompiler compiler;
    
    public abstract File getCatalogFile();

    protected void compileFile(final File sourceFile, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XdmNode document = builder.build(sourceFile);
        XdmNode documentRoot = (XdmNode) document.axisIterator(Axis.CHILD).next();
        if (documentRoot.getNodeName().getLocalName().equals("package")) {
            compilePackage(documentRoot, targetFile);
        } else {
            compileModule(documentRoot, targetFile);
        }
    }

    protected void compileModule(final XdmNode document, final File targetFile) throws SaxonApiException, FileNotFoundException {
        XsltExecutable exec = compiler.compile(document.asSource());
        targetFile.getParentFile().mkdirs();
        exec.export(new FileOutputStream(targetFile));
    }

    protected void compilePackage(final XdmNode document, final File targetFile) throws SaxonApiException {
        XsltPackage pack = compiler.compilePackage(document.asSource());
        pack.save(targetFile);
        compiler.importPackage(pack);
    }

    protected void initSaxon() {
        Configuration config = Configuration.newConfiguration();
        Processor proc = new Processor(config);
        compiler = proc.newXsltCompiler();
        Resolver uriResolver = new Resolver();
        if(getCatalogFile()!=null) {
            uriResolver.getCatalog().addSource(new CatalogSource.UriCatalogSource(getCatalogFile().toURI().toString()));
        }
        compiler.setURIResolver(uriResolver);
        builder = proc.newDocumentBuilder();
    }
    
    /**
     * Becaus ewe may need a compiler elswhere
     * @return 
     */
    protected XsltCompiler getXsltCompiler() { return compiler; }
    
}
