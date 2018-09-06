package io.tanker.notepad;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import io.tanker.api.Tanker;
import io.tanker.api.TankerDecryptOptions;

public class MainActivity extends AppCompatActivity {
    private String resourceId;
    private ArrayList<String> receivedNoteAuthors = new ArrayList<>();
    private ArrayList<String> receivedNoteContents = new ArrayList<>();
    private NotepadApplication mTankerApp;

    private URL getNoteUrl(String friendId) throws Throwable {
        if (friendId == null)
            friendId = mTankerApp.getUserId();
        return mTankerApp.makeURL("/data/" + friendId);
    }

    private URL putNoteUrl() throws Throwable {
        return mTankerApp.makeURL("/data");
    }

    private URL shareNoteUrl() throws Throwable {
        return mTankerApp.makeURL("/share");
    }

    private URL getMeUrl() throws Throwable {
        return mTankerApp.makeURL("/me");
    }

    private URL getUsersURL() throws Throwable {
        return mTankerApp.makeURL("/users");
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private String getUserIdFromEmail(String email) throws Throwable {
        URL url = getUsersURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream()));

        JSONArray users = new JSONArray(in.readLine());
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            if (user.has("email") && user.getString("email").equals(email))
                return user.getString("id");
        }
        Log.i("Notepad", "User to share not found");
        return null;
    }

    private void uploadToServer(byte[] encryptedData) throws Throwable {
        URL url = putNoteUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);

        String base64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP);
        Log.i("Notepad", base64);
        connection.getOutputStream().write(base64.getBytes());
        connection.getInputStream();
    }

    private byte[] dataFromServer(String userId) throws Throwable {
        URL url = getNoteUrl(userId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream()));
        String content = in.readLine();

        if (content == null) {
            return null;
        }
        return Base64.decode(content, Base64.DEFAULT);
    }

    private void loadSharedWithMe() throws Throwable {
        URL url = getMeUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream()));
        String data = in.readLine();

        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode json = jsonMapper.readTree(data);

        if (json.has("accessibleNotes")) {
            JsonNode notes = json.get("accessibleNotes");
            for (final JsonNode note : notes) {
                String authorEmail = note.get("email").asText();
                String authorUserId = note.get("id").asText();
                receivedNoteAuthors.add(authorEmail);
                receivedNoteContents.add(loadDataFromUser(authorUserId));
            }

            runOnUiThread(() -> {
                ListView notesList = findViewById(R.id.notes_list);
                notesList.setAdapter(new NoteListAdapter(this, R.layout.notes_list_item, receivedNoteAuthors, receivedNoteContents));
                for (String note : receivedNoteContents) {
                    //noinspection unchecked (this is fine)
                    ((ArrayAdapter<String>) notesList.getAdapter()).add(note);
                }
            });
        }
    }

    private String loadDataFromUser(String userId) {
        TankerDecryptOptions options = new TankerDecryptOptions();

        try {
            byte[] data = dataFromServer(userId);
            if (data == null) {
                return null;
            }

            Tanker tanker = ((NotepadApplication) getApplication()).getTankerInstance();
            byte[] clearData = tanker.decrypt(data, options).get();
            return new String(clearData, "UTF-8");
        } catch (Throwable e) {
            Log.e("Notepad", "loadDataError", e);
            return null;
        }
    }

    private void registerShareWithServer(String recipient) throws Throwable {
        URL url = shareNoteUrl();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);


        JsonObject data = new JsonObject();
        JsonArray recipientArray = new JsonArray();
        recipientArray.add(recipient);
        data.addProperty("from", mTankerApp.getUserId());
        data.add("to", recipientArray);
        Gson gson = new Gson();
        String jsonText = gson.toJson(data);

        Log.i("Notepad", jsonText);
        connection.getOutputStream().write(jsonText.getBytes());
        connection.getInputStream();

        int code = connection.getResponseCode();
        if (code >= 200 && code < 300)
            runOnUiThread(() -> {
                showToast("Share successfully");
            });
    }

    private void logout() {
        Tanker tanker = ((NotepadApplication) getApplication()).getTankerInstance();
        tanker.close().then((closeFuture) -> {
            runOnUiThread(() -> {
                // Redirect to the Login activity
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            });
            return null;
        });
    }

    private void setting() {
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    }

    private void share() {
        EditText recipientEdit = findViewById(R.id.recipient_name_edit);
        String recipientEmail = recipientEdit.getText().toString();

        ShareDataTask task = new ShareDataTask();
        task.execute(recipientEmail);
        boolean ok = false;

        try {
            ok = task.get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (!ok) {
            showToast("Share failed");
        }
    }

    private void saveData() {
        EditText contentEdit = findViewById(R.id.main_content_edit);
        String clearText = contentEdit.getText().toString();
        UploadDataTask task = new UploadDataTask();
        task.execute(clearText);
        boolean ok = false;
        try {
            ok = task.get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (!ok) {
            showToast("Upload failed");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTankerApp = (NotepadApplication) getApplicationContext();

        Button logoutButton = findViewById(R.id.main_logout_button);
        logoutButton.setOnClickListener((View v) -> logout());

        Button settingButton = findViewById(R.id.main_setting_button);
        settingButton.setOnClickListener((View v) -> setting());

        Button saveButton = findViewById(R.id.main_save_button);
        saveButton.setOnClickListener((View v) -> saveData());

        Button shareButton = findViewById(R.id.share_button);
        shareButton.setOnClickListener((View v) -> {
            saveData();
            share();
        });

        FetchDataTask backgroundTask = new FetchDataTask();
        backgroundTask.execute((String) null);
    }

    @Override
    public void onBackPressed() {
        logout();
    }

    public class FetchDataTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            String userId = params[0];
            String data = loadDataFromUser(userId);
            runOnUiThread(() -> {
                EditText contentEdit = findViewById(R.id.main_content_edit);
                contentEdit.setText(data);
            });

            try {
                loadSharedWithMe();
            } catch (Throwable e) {
                Log.e("Notepad", "Failed to fetch share data: " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    public class UploadDataTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String clearText = params[0];
            byte[] clearData;
            try {
                clearData = clearText.getBytes();
                Tanker tanker = ((NotepadApplication) getApplication()).getTankerInstance();
                byte[] encryptedData = tanker.encrypt(clearData, null).get();
                resourceId = tanker.getResourceID(encryptedData);
                uploadToServer(encryptedData);
            } catch (Throwable e) {
                Log.e("Notepad", "Failed to upload data: " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    public class ShareDataTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String recipientEmail = params[0];
            try {
                String recipientUserId = getUserIdFromEmail(recipientEmail);
                if (recipientUserId == null) {
                    Log.e("Notepad", "Failed to get the UserId from Email");
                    return false;
                }
                Tanker tanker = ((NotepadApplication) getApplication()).getTankerInstance();
                tanker.share(new String[]{resourceId}, new String[]{recipientUserId}).get();
                registerShareWithServer(recipientUserId);
            } catch (Throwable e) {
                Log.e("Notepad", "Failed to register share with server: " + e.getMessage());
                return false;
            }
            return true;
        }
    }
}