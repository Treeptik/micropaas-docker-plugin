package fr.treeptik.micropaas.plugins.docker;

import java.io.File;
import java.io.FilenameFilter;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import com.google.common.base.Strings;

@Mojo(name = "deploy")
public class DockerDeployMojo extends DockerMojo {

	/**
	 * Méthode principale du MOJO
	 * 
	 * @throws MojoExecutionException
	 */
	public void execute() throws MojoExecutionException {
		try {

			if (!isContainerExist(containerName)) {
				createContainer();
			}

			if (!isContainerUp(containerName)) {
				startContainer();
				// Wait container / service start
				Thread.sleep(2000);
			}

			File archiveFile = null;

			if (Strings.isNullOrEmpty(archivePath)) {
				
				String absolutePathWarFile = getAbsolutePathWarFile();
				getLog().debug("absolutePathWarFile : " + absolutePathWarFile);
				if (absolutePathWarFile != null) {
					String warFileName = absolutePathWarFile
							.substring(absolutePathWarFile.lastIndexOf("/") + 1);

					getLog().debug("warFileName : " + warFileName);
					archiveFile = new File(absolutePathWarFile);
					// Test if file exist. Maybe a plugin change the file name
					// (ex : maven-war-plugin)
					// And search for Ear or War
					if (!archiveFile.exists()) {
						getLog().warn("File not found : " + absolutePathWarFile);
						getLog().warn("Search other files (ear and war) in "
										+ getAbsoluteTargetDirectory());
						
						File[] files = listArchiveFiles();
						
						archiveFile = findApplicationInFiles(files);
						
					}
				}
			} else if ( ! Strings.isNullOrEmpty(archivePath)) {
				archiveFile = new File(archivePath);
			} else {
				throw new MojoExecutionException("Can't find archive application " + getContainerImage(), null);
			}

			String applicationName = archiveFile.getName().substring(0,
					archiveFile.getName().lastIndexOf("."));

			String sshForwardedPort = getForwardedPort("22");
			String ipDocker = getIpDocker();
			// Do not remove the final slash
			sendFile(archiveFile, sshForwardedPort, ipDocker, "/deploy/");
			// All caontainer images must have a script 'deploy.sh'
			executeShell(ipDocker, sshForwardedPort, "/bin/sh /deploy.sh "
					+ archiveFile.getName(), null);

			if (archiveFile.getName().endsWith(".war")) {
				String tomcatForwardPort = getForwardedPort("8080");
				StringBuilder msgInfo = new StringBuilder(1024);
				msgInfo.append("\n******************************************\n");
				msgInfo.append("******************************************\n");
				msgInfo.append("********** APPLICATION ACCESS ************\n");
				msgInfo.append("******************************************\n");
				msgInfo.append("******************************************\n");
				msgInfo.append("\nURL : http://").append(ipDocker).append(":")
						.append(tomcatForwardPort).append("/")
						.append(applicationName).append("\n");
				getLog().info(msgInfo);
			}

		} catch (Exception e) {
			throw new MojoExecutionException("Error deploying application "
					+ getContainerImage(), e);
		}
	}
	
	
	private File[] listArchiveFiles() {
		File directory = new File(getAbsoluteTargetDirectory());
		File[] files = directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File file, String name) {
				return (name.endsWith(".ear") || name.endsWith(".war") || name
						.endsWith(".jar")) ? true : false;
			}
		});
		
		return files;
	}
	
	private File findApplicationInFiles(File[] files) {

		File result = null;
		if (files.length > 1) {
			getLog().warn("Found more than one application, search ear file");
			for (File f : files) {
				if (f.getName().endsWith(".ear")) {
					getLog().info("Found pplication : " + f.getName());
					result = f;
					break;
				}
			}
		} else {
			getLog().info("Found application : " + files[0].getName());
			result = files[0];
		}

		return result;
	}

}
