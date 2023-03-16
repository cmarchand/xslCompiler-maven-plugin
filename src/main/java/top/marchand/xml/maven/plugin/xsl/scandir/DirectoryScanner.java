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
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import top.marchand.xml.maven.plugin.xsl.FileSet;

/**
 * A class to scan a directory and find files that match a {@link FileSet}
 * @since 1.0.0
 * @author cmarchand
 */
public class DirectoryScanner {
    private File baseDir;
    private final List<String> includes;
    private final List<String> excludes;
    private final Log log;
    
    // working attributes, not for state...
    private transient List<PathMatcher> includeMatchers;
    private transient List<PathMatcher> excludeMatchers;
    private transient boolean isToRecurse;
    private transient Path basePath;
    
    private ScanListener listener;
    
    /**
     * Constructs a DirectoryScanner on {@code baseDir}, with default includes
     * and default excludes.
     * @param baseDir The directory to scan
     * @param log The maven logger to use
     * @see FileSet#getDefaultIncludes() 
     * @see FileSet#getDefaultExcludes() 
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public DirectoryScanner(final File baseDir, final Log log) {
        super();
        this.baseDir = baseDir;
        this.includes = getDefaultIncludes();
        this.excludes = getDefaultExcludes();
        this.log=log;
    }
    
    /**
     * Constructs a DirectoryScanner for the specified FileSet
     * @param fileset The descriptoin of which files to get
     * @param projectBaseDir. The project base dir, to relocate {@link #baseDir} if {@code baseDir} does not exists
     * @param log The maven logger to use
     */
    public DirectoryScanner(final FileSet fileset, final File projectBaseDir, final Log log) {
        super();
        this.baseDir = new File(fileset.getDir());
        if(!this.baseDir.exists() || !baseDir.isDirectory()) {
            this.baseDir = new File(projectBaseDir, fileset.getDir());
        }
        this.includes = fileset.getIncludes();
        this.excludes = fileset.getExcludes();
        this.log=log;
        log.debug("DirectoryScanner<>(FileSet,Log) baseDir="+baseDir.getAbsolutePath());
    }
    /**
     * Returns default includes to use
     * Present to be overwritten.
     * @return The default includes
     */
    public List<String> getDefaultIncludes() {
        return FileSet.getDefaultIncludes();
    }
    /**
     * Returns default excludes to use. 
     * Present to be overwritten.
     * @return The default excludes
     */
    public List<String> getDefaultExcludes() {
        return FileSet.getDefaultExcludes();
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
            else if(include.matches(".*/.+/.*")) isToRecurse = true;
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
        if(listener!=null) {
            listener.scanning(dir);
        }
        log.debug("scanning "+dir.getAbsolutePath()+". it is "+(dir.isDirectory()?"":"not ")+"a directory");
        ArrayList<Path> ret = new ArrayList<>();
        File[] childs=dir.listFiles();
        log.debug("\tchilds="+Arrays.toString(childs));
        for(File child:childs) {
            if(child.isDirectory()) {
                if(isToRecurse) {
                    ret.addAll(scan(child));
                }
            } else {
                Path rel = basePath.relativize(child.toPath());
                boolean acceptable = false;
                for(PathMatcher pm:includeMatchers) {
                    if(pm.matches(rel)) {
                        acceptable = true;
                        break;
                    }
                }
                if(acceptable) {
                    for(PathMatcher pm:excludeMatchers) {
                        if(pm.matches(rel)) {
                            acceptable = false;
                            break;
                        }
                    }
                }
                if(acceptable) {
                    ret.add(rel);
                    if(listener!=null)
                        listener.fileAccepted(rel);
                } else {
                    if(listener!=null) listener.fileRejected(rel);
                }
            }
        }
        
        return ret;
    }
    
    /**
     * Return the directory where scan happens in
     * @return The base directory of scan
     */
    public File getBaseDir() { return baseDir; }

    /**
     * Sets the {@link ScanListener} to use
     * @param listener The listener to be notified
     */
    public void setScanListener(ScanListener listener) {
        this.listener = listener;
    }
}
