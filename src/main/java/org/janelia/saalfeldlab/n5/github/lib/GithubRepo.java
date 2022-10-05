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

package org.janelia.saalfeldlab.n5.github.lib;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GithubRepo {

	private final String url;
	private final Map<String, ObjectId> files;
	private final Git git;
	private final String branch;


	public GithubRepo(String url, String branch) throws GitAPIException, IOException {
		this.url = url;
		this.branch = branch;
		this.git = createGit(url);
		RevTree tree = createTree(git, branch);
		this.files = mapFiles(git, tree);
	}

	@Override
	public String toString() {
		return "GithubRepo{" +
				"url='" + url + '\'' +
				", branch='" + branch + '\'' +
				'}';
	}

	public static Map<String, ObjectId> mapFiles(Git git, RevTree tree) throws IOException {
		Map<String, ObjectId> result = new HashMap<>();
		TreeWalk treeWalk = new TreeWalk(git.getRepository());
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		while (treeWalk.next()) {
			result.put(treeWalk.getPathString(), treeWalk.getObjectId(0));
		}
		return result;
	}

	private static RevTree createTree(Git git, String branch) throws IOException {
		Repository repo = git.getRepository();
		repo.getObjectDatabase();
		ObjectId lastCommitId = repo.resolve("refs/heads/" + branch);
		RevWalk revWalk = new RevWalk(repo);
		RevCommit commit = revWalk.parseCommit(lastCommitId);
		return commit.getTree();
	}

	private static Git createGit(String url) throws GitAPIException {
		DfsRepositoryDescription repoDesc = new DfsRepositoryDescription();
		InMemoryRepository repo = new InMemoryRepository(repoDesc);
		Git git = new Git(repo);
		git.fetch()
				.setRemote(url)
				.setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*"))
				.call();
		return git;
	}

	public boolean exists(String path) {
		return this.files.containsKey(path);
	}

	public ObjectLoader readFile(String path) throws IOException {
		if (!exists(path))
			throw new IOException("File doesn't exist ! :" + path);

		return git.getRepository().open(files.get(path));
	}


	public Map<String, ObjectId> getFiles() {
		return files;
	}

	public static void main(String[] args) throws GitAPIException, IOException {
		GithubRepo githubRepo = new GithubRepo("https://github.com/github/testrepo.git", "master");
		System.out.println("created !");
		Map<String, ObjectId> files = githubRepo.getFiles();

		for (String k :
				files.keySet()) {
			System.out.println(k + ":" + files.get(k).getName());
		}

		githubRepo.readFile("test/alloc.c").copyTo(System.out);
	}
}
