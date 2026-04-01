package edu.msu.cse476.haidaris.finance_tracker;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ApiClient.java
 * Centralized HTTP helper for all calls to backend_API (port 8000).
 *
 * WHY THIS FILE EXISTS:
 * Instead of every Fragment building its own OkHttpClient and Request
 * objects from scratch, they all call ApiClient.get() or ApiClient.post().
 * This means:
 *   Base URL is defined in one place so we would change port or IP here only
 *   All fragments share one OkHttpClient instance (saves memory)
 *   Error handling is consistent everywhere
 *
 * USAGE IN A FRAGMENT:
 *   ApiClient.get("/transactions/" + uid, new ApiClient.ResponseCallback() {
 *       public void onSuccess(String responseBody) {
 *           // parse responseBody as JSON
 *       }
 *       public void onFailure(String error) {
 *           // show error toast
 *       }
 *   });
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // Base URL
    // 10.0.2.2 = localhost on your PC when running in Android emulator
    // Change to your PC's local IP (e.g. 192.168.1.x) for a real device
    private static final String BASE_URL = "http://10.0.2.2:8000";

    // Single shared OkHttpClient which is reused across all requests
    private static final OkHttpClient client = new OkHttpClient();

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Callback interface
    // Every caller implements this to handle success and failure
    public interface ResponseCallback {
        void onSuccess(String responseBody);
        void onFailure(String error);
    }

    // GET request
    /**
     * Makes an async GET request to BASE_URL + endpoint.
     * Calls callback.onSuccess(body) on success (any 2xx response).
     * Calls callback.onFailure(error) on network error or non-2xx.
     *
     * Example: ApiClient.get("/users/abc123", callback);
     */
    public static void get(String endpoint, ResponseCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    callback.onSuccess(body);
                } else {
                    Log.e(TAG, "GET " + endpoint + " failed: " + response.code() + " " + body);
                    callback.onFailure("Server error: " + response.code());
                }
                response.close();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "GET " + endpoint + " network error: " + e.getMessage());
                callback.onFailure("Network error: " + e.getMessage());
            }
        });
    }

    // POST request
    /**
     * Makes an async POST request with a JSON body.
     * body should be a JSONObject built by the caller.
     *
     * Example:
     *   JSONObject body = new JSONObject();
     *   body.put("firebase_uid", uid);
     *   body.put("amount", 45.00);
     *   ApiClient.post("/transactions", body, callback);
     */
    public static void post(String endpoint, JSONObject body, ResponseCallback callback) {
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    callback.onSuccess(responseBody);
                } else {
                    Log.e(TAG, "POST " + endpoint + " failed: " + response.code() + " " + responseBody);
                    callback.onFailure("Server error: " + response.code());
                }
                response.close();
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "POST " + endpoint + " network error: " + e.getMessage());
                callback.onFailure("Network error: " + e.getMessage());
            }
        });
    }
}