package hudson.util;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;

public class JsonResponseFactory {

    public static JSONObject getJsonObject(HttpResponse response) {
        if (!(response instanceof HttpResponses.JSONObjectResponse)) {
            throw new IllegalArgumentException("The response must be of type HttpResponses.JSONObjectResponse");
        }
        return ((HttpResponses.JSONObjectResponse) response).getJsonObject();
    }
}
