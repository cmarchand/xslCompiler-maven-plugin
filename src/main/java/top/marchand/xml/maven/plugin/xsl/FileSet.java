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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import top.marchand.xml.maven.plugin.xsl.scandir.DirectoryScanner;
import top.marchand.xml.maven.plugin.xsl.scandir.ScanListener;

/**
 * A FileSet entry. A FileSet is directory based, and have severals include and excludes.
 * A File that can traverse includes and being accepted, and that can traverse
 * excludes without being eliminated is member of FileSet.
 * @author cmarchand
 */
public class FileSet {

    private final List<String> includes;
    private final List<String> excludes;
    
    private List<Path> foundFiles;
    
    private String dir;
    
    /**
     * Constructs a new FileSet, with no base dir, with default includes and excludes
     */
    public FileSet() {
        includes = new ArrayList<>();
        excludes = new ArrayList<>();
        resetIncludeExcludes();
    }

    /**
     * Constructs a new FileSet, with default includes and excludes, based on dir.
     * @param dir The base directory of this FileSet
     */
    public FileSet(String dir) {
        this();
        this.dir=dir;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
    
    protected final void resetIncludeExcludes() {
        includes.clear();
        includes.addAll(getDefaultIncludes());
        excludes.clear();
        excludes.addAll(getDefaultExcludes());
    }
    public static List<String> getDefaultIncludes() {
        return Arrays.asList(new String[] {"*.xsl", "**/*.xsl"});
    }
    
    public static List<String> getDefaultExcludes() {
        return Arrays.asList(
            new String[] {
                "**/*~",                "**/#*#",
                "**/.#*",               "**/%*%",
                "**/._*",
                // CVS
                "**/CVS",               "**/CVS/**",
                "/.cvsignore",
                // SCCS
                "/SCCS",                "/SCCS/**",
                // Visual SourceSafe
                "/vssver.scc",
                // Subversion
                "/.svn",                "/.svn/**",
                // Git
                "/.git",                "/.git/**",
                "/.gitattributes",      "/.gitignore",
                "/.gitmodules",
                // Mercurial
                "/.hg",                "/.hg/**",
                "/.hgignore",          "/.hgsub",
                "/.hgsubstate",        "/.hgtags",
                // Bazaar
                "/.bzr",                "/.bzr/**",
                "/.bzrignore",
                // Mac
                "/.DS_Store"
            }
        );
    }
    
    /**
     * Returns the files that match this FileSet
     * @param projectBaseDir. Used to relocate {@link #dir } if <tt>dir</tt> does not exists
     * @param log The log to use while scanning. May be <tt>null</tt>
     * @return The files that match this FileSet
     */
    public List<Path> getFiles(File projectBaseDir, Log log) {
        return getFiles(projectBaseDir, log, null);
    }
    /**
     * Returns the files that match this FileSet
     * @param projectBaseDir. Used to relocate {@link #dir } if <tt>dir</tt> does not exists
     * @param log The log to use while scanning. May be <tt>null</tt>
     * @param listener The scan listener to use. May be <tt>null</tt>
     * @return The files that match this FileSet
     */
    public List<Path> getFiles(File projectBaseDir, Log log, ScanListener listener) {
        if(foundFiles==null) {
            DirectoryScanner scanner = new DirectoryScanner(this, projectBaseDir, log);
            scanner.setScanListener(listener);
            foundFiles = scanner.scan();
            this.dir=scanner.getBaseDir().getAbsolutePath();
        }
        return foundFiles;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[dir=").append(dir).append(", includes=").append(includes).append(", excludes=").append(excludes).append("]");
        return sb.toString();
    }
    
    
}
