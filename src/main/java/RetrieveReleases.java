import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

public class RetrieveReleases {

    private static final int MAX_RESULTS = 1000;

    /**
     * @param projectName name of the project
     * @return ArrayList; each entry is an array of 2 Strings (version, release date)
     */
    public static ArrayList<String[]> getReleases (String projectName) throws IOException, JSONException{
        int i = 0, total;
        ArrayList<String[]> results = new ArrayList<>();
        /*
          With a maximum of 1000 we should have all releases in one json file,
          so maxResult is hardcoded in url request
        */

        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName
                + "/version?maxResults=" + MAX_RESULTS + "&startAt="+ i;
        JSONObject json = JSONReader.readJsonFromUrl(url);
        total = json.getInt("total");
        boolean isLast = json.getBoolean("isLast");

        // let's check that the previous assumption (number of releases <= 1000) it's verified
        if (!isLast){
            throw new JSONException("Json output for releases contained more than" + MAX_RESULTS +"entries");
        }

        // parse json response to get version name and release date if they have been released
        JSONArray values = json.getJSONArray("values");
        for (; i < total; i++) {
            //Iterate through each version object
            JSONObject version_obj = values.getJSONObject(i);

            // version name (e.g. 1.0.5)
            String name = version_obj.getString("name");
            boolean released = version_obj.getBoolean("released");

            // store only the released versions !!!
            if (released) {
                // date is in format yyyy-MM-dd
                String releaseDate = version_obj.getString("releaseDate");
                results.add(new String[]{name, releaseDate});
            }
        }

        return results;
    }
}
