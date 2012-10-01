package mc.alk.plugin.updater;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginUpdater {
	public static void main(String[] args){
		downloadPluginUpdates(null);
	}
	
	public static void downloadPluginUpdates(final JavaPlugin plugin) {
		Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, new Runnable(){
			@Override
			public void run() {
				String strversion = plugin.getDescription().getVersion();
				Version curVersion = new Version(strversion);
				String pname = plugin.getName();
				File dir = plugin.getDataFolder();
				FileVersion fv = getGreatestVersion(plugin);

				downloadPluginUpdates(pname,curVersion,dir, fv);				
			}
		});
	}

	private static void downloadPluginUpdates(String pname, Version curVersion, File dir, FileVersion mostRecentDownload){
		final String bukkitURL = "http://dev.bukkit.org/";
		final String fileURL = bukkitURL+"server-mods/"+pname.toLowerCase()+"/files/";
		final int readTimeout = 5000;
		final int conTimeout = 7000;
		info("Plugin updater checking for updates for " + pname +" v" + curVersion);
		Version greatest = curVersion;
		String downloadLink = null;
		String line = null;
		try {
			final URLConnection connection = new URL(fileURL).openConnection();
			connection.setConnectTimeout(conTimeout);
			connection.setReadTimeout(readTimeout);

			final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			Pattern pattern = Pattern.compile("a href=\"(/server-mods/"+pname.toLowerCase()+"/files/[^\"]*)");
			Pattern pattern2 = Pattern.compile("("+pname+")[^<]*v([^<]*)");
			while ((line = br.readLine())!= null){
				Matcher matcher = pattern.matcher(line);
				if (!matcher.find()){
					continue;}				
				Matcher matcher2 = pattern2.matcher(line);
				line = line.replaceAll(matcher.group(), "");
				if (!matcher2.find()){
					continue;}
				Version v = new Version(matcher2.group(2));
				if (v.compareTo(greatest) <= 0){
					continue;}
				greatest = v;
				downloadLink = bukkitURL + matcher.group(1);				
			}
		} catch (Exception e) {
			return; /// Couldn't find updates
		}
		if (greatest == curVersion){ /// No update needed
			return;}

		if (mostRecentDownload != null && mostRecentDownload.version.compareTo(greatest) >= 0){
//			System.out.println(mostRecentDownload +" ########### " + mostRecentDownload.version.compareTo(greatest));
			return;} /// we already have downloaded a version that is equal or better
		info(pname +" found more recent version " + greatest);
		try {
			final URLConnection connection = new URL(downloadLink).openConnection();
			connection.setConnectTimeout(conTimeout);
			connection.setReadTimeout(readTimeout);
			downloadLink = null;
			final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			Pattern pattern = Pattern.compile("http:[^\"]*"+pname+".jar");
			while ((line = br.readLine())!= null){
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()){
					downloadLink = matcher.group(0);
					break;
				}
			}
			if (downloadLink != null){
				warn(pname+" downloading version " + greatest.getVersion());
				File updateDir = getUpdateDir(new File(dir.getAbsolutePath()));
				if (!updateDir.exists())
					updateDir.mkdir();

				String fname = updateDir.getAbsolutePath() +"/"+pname+"_"+greatest.getVersion();
				File saveFile = new File(fname+".dl");
				saveUrl(saveFile.getCanonicalPath(),downloadLink);
				saveFile.renameTo(new File(fname+".jar"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

	private static File getUpdateDir(File file) {
		return new File(file.getAbsoluteFile() + "/updates");
	}

	public static void saveUrl(String filename, String urlString) throws MalformedURLException, IOException {
		final int bufSize = 2048;
		BufferedInputStream in = null;
		FileOutputStream fout = null;
		try {
			in = new BufferedInputStream(new URL(urlString).openStream());
			fout = new FileOutputStream(filename);

			byte data[] = new byte[bufSize];
			int count;
			while ((count = in.read(data, 0, bufSize)) != -1){
				fout.write(data, 0, count);}
		} finally {
			if (in != null)
				in.close();
			if (fout != null)
				fout.close();
		}
	}
	
	public static class FileVersion{
		File file;
		Version version;
		public FileVersion(File file, Version version){
			this.file = file;
			this.version = version;
		}
		public String toString(){
			return file.getPath()+"  version="+version;
		}
	}

	private static FileVersion getGreatestVersion(JavaPlugin plugin){
		Version curVersion = new Version(plugin.getDescription().getVersion());
		File updateDir = getUpdateDir(plugin.getDataFolder());
		if (!updateDir.exists())
			return null;
		String strversion = null;
		FileVersion fv = new FileVersion(updateDir,curVersion);
		
		FileVersion greatest = fv;
		for (String file: updateDir.list()){
			strversion = file.replace(".jar", "");
			String split[] = strversion.split("_");
			if (split.length == 0)
				continue;
//			System.out.println("FFFFFFFFFFFFFFFFFFFFFFFFF = " + file);
			Version fileVersion = new Version(split[split.length-1].trim());
			if (fileVersion.compareTo(greatest.version) > 0){
				File f = new File(updateDir.getPath()+"/"+file);
//				System.out.println("################################### " + f);
				greatest = new FileVersion(f, fileVersion);
			}
		}
//		System.out.println("GREATEST === " + greatest);
		return greatest == fv ? null : greatest;
	}
	
	public static void updatePlugin(JavaPlugin plugin) {
		FileVersion fv = getGreatestVersion(plugin);
		if (fv == null)
			return;
		File jarFile = new File("plugins/"+plugin.getName()+".jar");
//		System.out.println("#!!!!!!!!!!!!!!!!!!!!!!!  " + fv.file.getPath() +"   " + jarFile.getPath());
//		System.out.println("#!!!!!!!!!!!!!!!!!!!!!!!  " + fv.file.getAbsolutePath() +"   " + jarFile.getAbsolutePath());

		try {
			if (renameFile(fv.file, jarFile)){
				warn(plugin.getName() +" updated to " + fv.version);
				File updateDir = getUpdateDir(plugin.getDataFolder());
				deleteDir(updateDir);
			} else {
				err("Couldnt rename "+fv.file+" to " + jarFile.getAbsolutePath());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
//	public static boolean renameFile(File src, File dest){
//		Random r = new Random();
//		File temp = new File(src.getAbsolutePath()+"__"+r.nextInt());
//		System.out.println("######## " + temp.getPath());
//		if (!dest.renameTo(temp))
//			return false;
//		System.out.println("########2 " + src.getPath() +"   " + dest.getPath());
//		if (!src.renameTo(dest)){
//			temp.renameTo(dest);
//		}
//		
//		return true;
//	}
	
//	public static boolean renameFile(File srcFile, File destFile) throws IOException {
//	    boolean bSucceeded = false;
//	    try {
//	        if (destFile.exists()) {
//	            if (!destFile.delete()) {
//	                throw new IOException(srcFile.getName() + " was not successfully renamed to " + destFile.getName()); 
//	            }
//	        }
//	        if (!srcFile.renameTo(destFile))        {
//	            throw new IOException(srcFile.getName() + " was not successfully renamed to " + destFile.getName());
//	        } else {
//	                bSucceeded = true;
//	        }
//	    } finally {
//	          if (bSucceeded) {
//	                srcFile.delete();
//	          }
//	    }
//	    return bSucceeded;
//	}

//	public static boolean renameFile(String oldName, String newName) throws IOException {
//	    File srcFile = new File(oldName);
//	    boolean bSucceeded = false;
//	    try {
//	        File destFile = new File(newName);
//	        if (destFile.exists()) {
//	            if (!destFile.delete()) {
//	                throw new IOException(oldName + " was not successfully renamed to " + newName); 
//	            }
//	        }
//	        if (!srcFile.renameTo(destFile))        {
//	            throw new IOException(oldName + " was not successfully renamed to " + newName);
//	        } else {
//	                bSucceeded = true;
//	        }
//	    } finally {
//	          if (bSucceeded) {
//	                srcFile.delete();
//	          }
//	    }
//	    return bSucceeded;
//	}
//	public static boolean renameFile(File src, File dest){
//		InputStream inputStream = null;
//		try {
//			inputStream = new FileInputStream(src);
//		} catch (FileNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		if (inputStream == null){
//			return false;
//		}
//		OutputStream out = null;
//		try{
//			out=new FileOutputStream(dest);
//			byte buf[]=new byte[1024];
//			int len;
//			while((len=inputStream.read(buf))>0){
//				out.write(buf,0,len);}
//		} catch (Exception e){
//			e.printStackTrace();
//			return false;
//		} finally{
//			if (out != null)
//				try {out.close();} catch (IOException e) {}
//			if (inputStream!=null) 
//				try {inputStream.close();} catch (IOException e) {}
//		}
//		return true;
//
//	}
	
	public static boolean renameFile(File sourceFile, File destFile) throws IOException {
	    if(!destFile.exists()) {
	        destFile.createNewFile();
	    }

	    FileChannel source = null;
	    FileChannel destination = null;

	    try {
	        if (destFile.exists()){
	        	if (!destFile.delete()){
//	        		System.err.println("EEEEEEEEEEEEE coudlnt delete!!!");
	        		return false;
	        	}
	        }
	        source = new FileInputStream(sourceFile).getChannel();
	        destination = new FileOutputStream(destFile).getChannel();
	        destination.transferFrom(source, 0, source.size());
	    } catch(Exception e){
	    	e.printStackTrace();
	    	return false;
	    }
	    finally {
	        if(source != null) {
	            source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
	    return true;
	}

	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i=0; i<children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		return dir.delete();
	}
	public static void info(String msg){
		Logger log = Bukkit.getLogger();
		if (log != null){
			log.info(msg);
		} else {
			System.out.println(msg);
		}
	}
	public static void warn(String msg){
		Logger log = Bukkit.getLogger();
		if (log != null){
			log.warning(msg);
		} else {
			System.out.println(msg);
		}
	}
	public static void err(String msg){
		Logger log = Bukkit.getLogger();
		if (log != null){
			log.severe(msg);
		} else {
			System.err.println(msg);
		}
	}
}
