package git;

import logging.LoggerSingleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.logging.Level;

public class GitSingleton {

    private static GitSingleton instance = null;
    private Git git = null;
    private final String repo_path;

    public static synchronized GitSingleton getInstance(){
        if (instance == null)
            instance = new GitSingleton();
        return instance;
    }

    protected GitSingleton(){
        this.repo_path = parseConfiguration();
    }

    private String parseConfiguration(){
        JSONParser parser = new JSONParser();
        String result = null;
        try {
            InputStream resource = getClass().getClassLoader().getResourceAsStream("config.json");
            if (resource == null){
                throw new IllegalArgumentException("Configuration file not found");
            }else {
                 BufferedReader config = new BufferedReader(new InputStreamReader(resource));
                 JSONObject obj = (JSONObject) parser.parse(config);
                 result = (String) obj.get("repo");
            }
        } catch (FileNotFoundException e) {
            LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "File not found", e);
        } catch (IOException | ParseException e) {
            LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "JSON parsing error", e);
        }

        return result;
    }

    public synchronized Git getGit(){
        if (this.git == null){
            try {
                FileRepositoryBuilder repoBuilder = new FileRepositoryBuilder();
                Repository repo = repoBuilder.setGitDir(new File(this.repo_path + "\\.git")).setMustExist(true).build();
                this.git = new Git(repo);
            } catch (IOException e) {
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Exception JGit in building repository", e);
            } catch (NullPointerException e){
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Null pointer exception: resource parsing resulted in null path", e);
            }
        }

        return this.git;
    }
}
