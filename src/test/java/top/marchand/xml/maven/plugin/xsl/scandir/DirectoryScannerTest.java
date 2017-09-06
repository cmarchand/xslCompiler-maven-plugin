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
package top.marchand.xml.maven.plugin.xsl.scandir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import top.marchand.xml.maven.plugin.xsl.FileSet;

/**
 * Tests {@link DirectoryScanner}
 * @author cmarchand
 */
public class DirectoryScannerTest {
    private static Log log;
    
    @BeforeClass
    public static void beforeClass() {
        log = new DefaultLog(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "TEST")) {
            @Override
            public boolean isDebugEnabled() { return true; }
        };

    }
    
    @Test
    public void testDefault() {
        DirectoryScanner scanner = new DirectoryScanner(
                new File("src/test/resources/DirectoryScanner"),
                log
        );
        List<Path> ret = scanner.scan();
        assertEquals("6 files were expected with defaults", 6, ret.size());
    }
    
    @Test
    public void test2chars() {
        FileSet fileset = new FileSet("src/test/resources/DirectoryScanner");
        fileset.getIncludes().clear();
        fileset.getIncludes().add("**/??.xsl");
        assertEquals("fileset.getIncludes() have the wrong number of items", 1, fileset.getIncludes().size());
        DirectoryScanner scanner = new DirectoryScanner(fileset, log);
        List<Path> ret = scanner.scan();
        assertEquals("3 files were expected with 2 chars in name", 3, ret.size());
    }
    
    @Test
    public void testDirFilter() {
        FileSet fileset = new FileSet("src/test/resources/DirectoryScanner");
        fileset.getIncludes().clear();
        fileset.getIncludes().add("**/xml/**/*.xsl");
        fileset.getIncludes().add("**/xml/*.xsl");
        assertEquals("fileset.getIncludes() have the wrong number of items", 2, fileset.getIncludes().size());
        DirectoryScanner scanner = new DirectoryScanner(fileset, log);
        List<Path> ret = scanner.scan();
        assertEquals("4 files were expected under xml/ dir", 4, ret.size());
    }
    
    @Test
    public void testFullPath() {
        FileSet fileset = new FileSet("src/test/resources/DirectoryScanner");
        fileset.getIncludes().clear();
        fileset.getIncludes().add("pipes/form/prepare.xml");
        DirectoryScanner scanner = new DirectoryScanner(fileset, log);
        List<Path> ret = scanner.scan();
        assertEquals("Only one file should be found", 1, ret.size());
        assertEquals("pipes/form/prepare.xml", ret.get(0).toString());
    }
}
