package jira;

import json.JSONReader;
import model.JiraTicket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class RetrieveTicketsID {

    private RetrieveTicketsID() {}


    /**
     * Assumptions: retrieve only tickets of type <i>Bug</i> and that are closed
     * or resolved with resolution <i>Fixed</i>.
     * @param projectName the name of the project
     * @return A list of tickets
     * @throws IOException Error in IO communication
     * @throws JSONException Error occurred while parsing the JSON response from Jira
     */
    public static List<JiraTicket> getTicketsID(String projectName) throws IOException, JSONException {
        int j;
        int i = 0;
        int total;
        //Get JSON API for closed bugs w/ AV in the project
        ArrayList<JiraTicket> results = new ArrayList<>();
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
                JiraTicket ticket = parseJsonTicket(issues.getJSONObject(i % 1000));
                results.add(ticket);
            }
        } while (i < total);

        return results;
    }

    private static JiraTicket parseJsonTicket(JSONObject ticket){
        String key = ticket.get("key").toString();
        JSONObject fields = ticket.getJSONObject("fields");

        // parsing the affected versions
        JSONArray versions = fields.getJSONArray("versions");
        ArrayList<String> parsedVersions = new ArrayList<>();
        for (int i = 0; i < versions.length(); i++){
            JSONObject version = versions.getJSONObject(i);
            boolean released = version.getBoolean("released");
            if (released) {
                parsedVersions.add(version.getString("name"));
            }
        }
        // parsing creation and fixing dates
        String creation = fields.getString("created").substring(0,10);
        String fix = fields.getString("resolutiondate").substring(0,10);
        return new JiraTicket(key, creation, fix, parsedVersions);
    }
}
