package mc.alk.plugin.updater;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

public class FileUpdater {

	File oldFile;
	File backupDir;

	Version updateVersion;
	Version oldVersion;

	public static enum SearchType{
		CONTAINS, MATCHES
	}
	public static enum UpdateType{
		ADDAFTER, REPLACE, DELETE
	}

	public static class Update{
		public final UpdateType type;
		public final String search;
		public final String[] updates;
		public final SearchType searchType;
		public Update(String search, UpdateType type, SearchType searchType, String... strings){
			this.type = type;
			this.search = search;
			this.updates = strings;
			this.searchType = searchType;
		}
	}

	HashMap<String, Update> updates = new HashMap<String, Update>();

	public FileUpdater(File oldFile, File backupDir, Version newVersion, Version oldVersion){
		this.oldFile = oldFile.getAbsoluteFile();
		this.backupDir = backupDir.getAbsoluteFile();
		this.updateVersion = newVersion;
		this.oldVersion = oldVersion;
	}
	public void delete(String str){
		updates.put(str, new Update(str,UpdateType.DELETE, SearchType.MATCHES, ""));
	}
	public void addAfter(String str, String...strings){
		updates.put(str, new Update(str,UpdateType.ADDAFTER, SearchType.MATCHES, strings));
	}
	public void replace(String str, String...strings){
		updates.put(str, new Update(str,UpdateType.REPLACE, SearchType.MATCHES, strings));
	}

	public Version update(){
		System.out.println("[Plugin Updater] updating " + oldFile.getName() +" from "+ oldVersion+" to " + updateVersion);
		System.out.println("[Plugin Updater] old version backup inside of " + backupDir.getAbsolutePath());
		BufferedReader br = null;
		BufferedWriter fw = null;
		File tempFile = null;
		try {
			br = new BufferedReader(new FileReader(oldFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}

		try {
			tempFile = new File(backupDir+"/temp-"+new Random().nextInt()+".yml");
			fw = new BufferedWriter(new FileWriter(tempFile));
		} catch (IOException e) {
			e.printStackTrace();
			try{br.close();}catch (Exception e2){}
			return null;
		}

		String line =null;
		try {
			while ((line = br.readLine()) != null){
				boolean foundMatch = false;
				for (Entry<String,Update> entry : updates.entrySet()){
					Update up = entry.getValue();
					switch(up.searchType){
					case MATCHES:
						if (!line.matches(up.search)) {
							continue;}
						break;
					case CONTAINS:
						if (!line.contains(up.search)){
							continue;}
						break;
					}
					if (up.type == UpdateType.ADDAFTER){ /// add back in the original search line
						fw.write(line+"\n");}
					if (up.type != UpdateType.DELETE)
						for (String update: up.updates){
							fw.write(update+"\n");}
					foundMatch = true;
					break;
				}
				if (!foundMatch){
					fw.write(line+"\n");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			try {br.close();} catch (Exception e) {}
			try {fw.close();} catch (Exception e) {}
		}
		String nameWithoutExt = oldFile.getName().replaceFirst("[.][^.]+$", "");
		String ext = oldFile.getName().substring(nameWithoutExt.length()+1);
		if (ext == null || ext.isEmpty())
			ext =".bk";
		copy(oldFile, new File(backupDir+"/"+nameWithoutExt+"."+oldVersion+"."+ext));
		return renameTo(tempFile, oldFile) ? updateVersion : null;
	}

	private static boolean renameTo(File file1, File file2) {
		/// That's right, I can't just rename the file, i need to move and delete
		if (PluginUpdater.isWindows()){
			File temp = new File(file2.getAbsoluteFile() +"."+new Random().nextInt()+".backup");
			if (temp.exists()){
				temp.delete();}

			if (file2.exists()){
				file2.renameTo(temp);
				file2.delete();
			}
			if (!file1.renameTo(file2)){
				System.err.println(temp.getName() +" could not be renamed to " + file2.getName());
				return false;
			} else {
				temp.delete();
				return true;
			}
		} else {
			if (!file1.renameTo(file2)){
				System.err.println(file1.getName() +" could not be renamed to " + file2.getName());
				return false;
			}
			return true;
		}
	}


	public void copy(File file1, File file2) {
		try{
			if (file2.exists()){
				file2.delete();}
			InputStream inputStream = new FileInputStream(file1);
			OutputStream out=new FileOutputStream(file2);
			byte buf[]=new byte[1024];
			int len;
			while((len=inputStream.read(buf))>0){
				out.write(buf,0,len);}
			out.close();
			inputStream.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
}
