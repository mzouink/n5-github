/*
 * *
 *  * Copyright (c) 2022, Janelia
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  *
 *  * 1. Redistributions of source code must retain the above copyright notice,
 *  *    this list of conditions and the following disclaimer.
 *  * 2. Redistributions in binary form must reproduce the above copyright notice,
 *  *    this list of conditions and the following disclaimer in the documentation
 *  *    and/or other materials provided with the distribution.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.janelia.saalfeldlab.n5.github;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.github.lib.GithubRepo;
import org.janelia.saalfeldlab.n5.github.lib.N5GithubBackend;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.HashMap;

public class N5GithubReader extends AbstractGsonReader implements N5Reader {
    private final GithubRepo repo;
    private final String subPath;

    private N5GithubReader(Builder builder) throws IOException, GitAPIException {
        super(builder.gsonBuilder);
        this.subPath = builder.subPath;
        this.repo = new GithubRepo(builder.url, builder.branch);
    }

    public static class Builder {
        private final String url;
        public GsonBuilder gsonBuilder = new GsonBuilder();
        private String branch = "main";
        private N5GithubBackend backend = N5GithubBackend.N5;
        private String subPath = "";

        public Builder setBranch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder setBackend(N5GithubBackend backend) {
            this.backend = backend;
            return this;
        }

        public Builder setSubPath(String subPath) {
            this.subPath = subPath;
            return this;
        }

        public Builder setGsonBuilder(GsonBuilder gsonBuilder) {
            this.gsonBuilder = gsonBuilder;
            return this;
        }

        private Builder(String url) {
            this.url = url;
        }

        private N5Reader build() throws GitAPIException, IOException {
            switch (backend) {
                case N5:
                    return new N5GithubReader(this);
                case Zarr:
                    throw new IOException("Zarr not implemented yet!");
                default:
                    throw new IOException(backend.name() + " not implemented yet!");
            }
        }


        private static Builder builder(String url) {
            return new Builder(url);
        }
    }


    public boolean exists(String pathName) {
        String fullPath = this.getFullPath(pathName);
        if (fullPath.isEmpty())
            return true;
        boolean result = repo.exists(pathName);
        System.out.println("File " + pathName + " : " + result);
        return result;
    }

    public HashMap<String, JsonElement> getAttributes(String pathName) throws IOException {
        String attributesKey = this.getAttributesKey(pathName);
        if (!repo.exists(attributesKey)) {
            System.out.println("Attribute key not found " + attributesKey);
            return new HashMap();
        } else {
            InputStream in = this.readGithubObject(attributesKey);
            Throwable var4 = null;

            HashMap var5;
            try {
                var5 = GsonAttributesParser.readAttributes(new InputStreamReader(in), this.gson);
            } catch (Throwable var14) {
                var4 = var14;
                throw var14;
            } finally {
                if (in != null) {
                    if (var4 != null) {
                        try {
                            in.close();
                        } catch (Throwable var13) {
                            var4.addSuppressed(var13);
                        }
                    } else {
                        in.close();
                    }
                }

            }

            return var5;
        }
    }

    public DataBlock<?> readBlock(String pathName, DatasetAttributes datasetAttributes, long... gridPosition) throws IOException {
        String dataBlockKey = this.getDataBlockKey(pathName, gridPosition);
        if (!repo.exists(dataBlockKey)) {
            System.out.println("Attribute key not found " + dataBlockKey);
            return null;
        } else {
            InputStream in = this.readGithubObject(dataBlockKey);
            Throwable var6 = null;

            DataBlock var7;
            try {
                var7 = DefaultBlockReader.readBlock(in, datasetAttributes, gridPosition);
            } catch (Throwable var16) {
                var6 = var16;
                throw var16;
            } finally {
                if (in != null) {
                    if (var6 != null) {
                        try {
                            in.close();
                        } catch (Throwable var15) {
                            var6.addSuppressed(var15);
                        }
                    } else {
                        in.close();
                    }
                }

            }

            return var7;
        }
    }

    public String[] list(String pathName) {
        String fullPath = this.getFullPath(pathName);
        if (fullPath.isEmpty())
            return repo.getFiles().keySet().stream().toArray(String[]::new);
        else
            return repo.getFiles().keySet().stream().filter(e -> e.contains(fullPath)).toArray(String[]::new);
    }

    protected InputStream readGithubObject(String objectKey) throws IOException {
        ObjectLoader loader = repo.readFile(objectKey);
        return loader.openStream();
    }

    protected static String replaceBackSlashes(String pathName) {
        return pathName.replace("\\", "/");
    }

    protected static String removeLeadingSlash(String pathName) {
        return !pathName.startsWith("/") && !pathName.startsWith("\\") ? pathName : pathName.substring(1);
    }

    protected static String addTrailingSlash(String pathName) {
        return !pathName.endsWith("/") && !pathName.endsWith("\\") ? pathName + "/" : pathName;
    }

    protected String getDataBlockKey(String datasetPathName, long... gridPosition) {
        String[] pathComponents = new String[gridPosition.length];

        for (int i = 0; i < pathComponents.length; ++i) {
            pathComponents[i] = Long.toString(gridPosition[i]);
        }

        String dataBlockPathName = Paths.get(removeLeadingSlash(datasetPathName), pathComponents).toString();
        return this.getFullPath(dataBlockPathName);
    }

    protected String getFullPath(String relativePath) {
        String fullPath = Paths.get(removeLeadingSlash(this.subPath), relativePath).toString();
        return removeLeadingSlash(replaceBackSlashes(fullPath));
    }

    protected String getAttributesKey(String pathName) {
        String attributesPath = Paths.get(removeLeadingSlash(pathName), "attributes.json").toString();
        return this.getFullPath(attributesPath);
    }

    public static void main(String[] args) throws GitAPIException, IOException {
        String url = "https://github.com/mzouink/n5_image.git";
        String branch = "main";
        String dataset = "setup0/timepoint0/s0";

        N5Reader reader = new N5GithubReader.Builder(url)
//                .setBranch(branch)
//				.setSubPath("")
                .setBackend(N5GithubBackend.N5)
                .build();

        System.out.println("Created");
        CachedCellImg<FloatType, ?> img = N5Utils.open(reader, dataset);
        ImageJFunctions.show(img);
    }

}
