package jira;

import json.JSONReader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class RetrieveTicketsID {

    private RetrieveTicketsID() {}


    public static List<String> getTicketsID(String projectName) throws IOException, JSONException {
        int j;
        int i = 0;
        int total;
        //Get JSON API for closed bugs w/ AV in the project
        ArrayList<String> results = new ArrayList<>();
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projectName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json = JSONReader.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                String key = issues.getJSONObject(i % 1000).get("key").toString();
                results.add(key);
            }
        } while (i < total);

        return results;
    }
}
