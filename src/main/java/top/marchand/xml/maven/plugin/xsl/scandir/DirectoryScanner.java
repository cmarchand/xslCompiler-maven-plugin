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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import top.marchand.xml.maven.plugin.xsl.FileSet;

/**
 * A class to scan a directory and find files that match a {@link FileSet}
 * @since 1.0.0
 * @author cmarchand
 */
public class DirectoryScanner {
    private final File baseDir;
    private final List<String> includes;
    private final List<String> excludes;
    private final Log log;
    
    // working attributes, not for state...
    private transient List<PathMatcher> includeMatchers;
    private transient List<PathMatcher> excludeMatchers;
    private transient boolean isToRecurse;
    private transient Path basePath;
    
    /**
     * Constructs a DirectoryScanner on <tt>baseDir</tt>, with default includes
     * and default excludes.
     * @param baseDir The directory to scan
     * @param log The maven logger to use
     * @see FileSet#getDefaultIncludes() 
     * @see FileSet#getDefaultExcludes() 
     */
    public DirectoryScanner(final File baseDir, final Log log) {
        super();
        this.baseDir = baseDir;
        this.includes = FileSet.getDefaultIncludes();
        this.excludes = FileSet.getDefaultExcludes();
        this.log=log;
    }
    
    /**
     * Constructs a DirectoryScanner for the specified FileSet
     * @param fileset The descriptoin of which files to get
     * @param log The maven logger to use
     */
    public DirectoryScanner(final FileSet fileset, final Log log) {
        super();
        this.baseDir = new File(fileset.getDir());
        this.includes = fileset.getIncludes();
        this.excludes = fileset.getExcludes();
        this.log=log;
    }
    /**
     * Returns a list of pathes relative to fileset's basedir
     * @return Found pathes
     */
    public List<Path> scan() {
        prepareFilters();
        return scan(baseDir);
    }

    /**
     * Initialize working attributes.
     */
    protected void prepareFilters() {
        FileSystem fs = FileSystems.getDefault();
        includeMatchers = new ArrayList<>(includes.size());
        for(String include: includes) {
            includeMatchers.add(buildPathMatcher(fs, include));
            if(include.contains("**")) isToRecurse = true;
        }
        excludeMatchers = new ArrayList<>(excludes.size());
        for(String exclude: excludes) {
            excludeMatchers.add(buildPathMatcher(fs, exclude));
        }
        basePath = baseDir.toPath().normalize();
    }
    private PathMatcher buildPathMatcher(final FileSystem fs, final String pattern) {
        String _pattern = (pattern.startsWith("glob:") || pattern.startsWith("regex:")) ? pattern : "glob:"+pattern;
        return fs.getPathMatcher(_pattern);
    }
    
    protected List<Path> scan(File dir) {
        ArrayList<Path> ret = new ArrayList<>();
        for(File child:dir.listFiles()) {
            if(child.isDirectory()) {
                if(isToRecurse) {
                    ret.addAll(scan(child));
                }
            } else {
                Path rel = basePath.relativize(child.toPath());
                boolean acceptable = false;
                for(PathMatcher pm:includeMatchers) {
                    if(pm.matches(rel)) {
                        if(log!=null && log.isDebugEnabled())
                            log.debug("[INCLUDE] "+rel+" matches "+pm);
                        acceptable = true;
                        break;
                    } else {
                        if(log!=null && log.isDebugEnabled())
                            log.debug("[INCLUDE] "+rel+" does not match "+pm);
                    }
                }
                for(PathMatcher pm:excludeMatchers) {
                    if(pm.matches(rel)) {
                        if(log!=null && log.isDebugEnabled())
                            log.debug("[EXCLUDE] "+rel+" matches "+pm);
                        acceptable = false;
                        break;
                    }
                }
                if(acceptable) ret.add(rel);
            }
        }
        
        return ret;
    }
}
