package com.talktranslate.app;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // Point this at your middleman server. Plain http is intentional here -
    // Android 4.0.4 only negotiates up to TLS 1.0, so pushing HTTPS to modern
    // APIs through this device directly will not work. Let the middleman do
    // that negotiation, and keep this link on a trusted LAN/VPN, or set
    // AUTH_TOKEN below for a lightweight shared-secret check.
    private static final String BASE_URL = "http://100.97.73.124:8000";
    private static final String DEFINE_ENDPOINT = BASE_URL + "/define";
    private static final String LANG = "en";

    // Optional shared-secret header, checked by the middleman if you want it.
    // Leave blank to send no auth header at all.
    private static final String AUTH_TOKEN = "";

    private static final float HALO_IDLE_ALPHA = 0.5f;
    private static final float HALO_LISTENING_ALPHA = 1f;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening;

    private View card;
    private TextView tvWord;
    private TextView tvPhonetic;
    private TextView tvDefinition;
    private View synonymsDivider;
    private View synonymsHeader;
    private FlowLayout flowSynonyms;
    private ProgressBar progressBar;
    private View halo;
    private ImageButton btnSpeak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        card = findViewById(R.id.card);
        tvWord = (TextView) findViewById(R.id.tvWord);
        tvPhonetic = (TextView) findViewById(R.id.tvPhonetic);
        tvDefinition = (TextView) findViewById(R.id.tvDefinition);
        synonymsDivider = findViewById(R.id.synonymsDivider);
        synonymsHeader = findViewById(R.id.synonymsHeader);
        flowSynonyms = (FlowLayout) findViewById(R.id.flowSynonyms);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        halo = findViewById(R.id.halo);
        btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.error_recognizer_unavailable, Toast.LENGTH_LONG).show();
            btnSpeak.setEnabled(false);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(recognitionListener);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        btnSpeak.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startListening();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopListening();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void startListening() {
        // A second ACTION_DOWN before the previous session finished tearing down
        // is what the service answers with ERROR_RECOGNIZER_BUSY (8). Guard on our
        // own state, then cancel() to clear any half-torn-down session - cancel()
        // is a no-op when the recognizer is already idle.
        if (isListening) {
            return;
        }
        isListening = true;
        halo.setAlpha(HALO_LISTENING_ALPHA);
        speechRecognizer.cancel();
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        if (!isListening) {
            return;
        }
        isListening = false;
        speechRecognizer.stopListening();
        halo.setAlpha(HALO_IDLE_ALPHA);
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {}

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            isListening = false;
            halo.setAlpha(HALO_IDLE_ALPHA);
            // Without this the failed session can linger and the next press comes
            // back as ERROR_RECOGNIZER_BUSY instead of the real error.
            speechRecognizer.cancel();
            Toast.makeText(MainActivity.this, describeSpeechError(error), Toast.LENGTH_SHORT).show();
        }

        private String describeSpeechError(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_NETWORK:
                    return getString(R.string.error_speech_network);
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    return getString(R.string.error_speech_network_timeout);
                case SpeechRecognizer.ERROR_AUDIO:
                    return getString(R.string.error_speech_audio);
                case SpeechRecognizer.ERROR_SERVER:
                    return getString(R.string.error_speech_server);
                case SpeechRecognizer.ERROR_CLIENT:
                    return getString(R.string.error_speech_client);
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    return getString(R.string.error_speech_busy);
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    return getString(R.string.error_speech_permission);
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                default:
                    return getString(R.string.error_no_speech);
            }
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String heard = matches.get(0);
                showWord(heard);
                sendToMiddleman(heard);
            } else {
                Toast.makeText(MainActivity.this, R.string.error_no_speech, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };

    private void showWord(String word) {
        card.setVisibility(View.VISIBLE);
        tvWord.setText(word);
        tvPhonetic.setText("");
        tvPhonetic.setVisibility(View.GONE);
        tvDefinition.setText("");
        hideSynonyms();
    }

    private void hideSynonyms() {
        synonymsDivider.setVisibility(View.GONE);
        synonymsHeader.setVisibility(View.GONE);
        flowSynonyms.setVisibility(View.GONE);
        flowSynonyms.removeAllViews();
    }

    private void sendToMiddleman(String heardText) {
        progressBar.setVisibility(View.VISIBLE);
        new DefineTask().execute(heardText, LANG);
    }

    private class DefineTask extends AsyncTask<String, Void, DefinitionResult> {

        @Override
        protected DefinitionResult doInBackground(String... params) {
            String text = params[0];
            String lang = params[1];

            HttpURLConnection connection = null;
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("text", text);
                requestBody.put("lang", lang);

                URL url = new URL(DEFINE_ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (AUTH_TOKEN.length() > 0) {
                    connection.setRequestProperty("Authorization", "Bearer " + AUTH_TOKEN);
                }

                OutputStream out = connection.getOutputStream();
                out.write(requestBody.toString().getBytes("UTF-8"));
                out.flush();
                out.close();

                int status = connection.getResponseCode();
                InputStream stream = (status >= 200 && status < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String responseBody = readStream(stream);

                if (status < 200 || status >= 300) {
                    return DefinitionResult.failure("Server returned " + status);
                }

                JSONObject json = new JSONObject(responseBody);
                String definition = json.optString("definition", "");
                String phonetic = json.optString("phonetic", "");

                ArrayList<String> synonyms = new ArrayList<String>();
                JSONArray synonymsArray = json.optJSONArray("synonyms");
                if (synonymsArray != null) {
                    for (int i = 0; i < synonymsArray.length(); i++) {
                        String s = synonymsArray.optString(i, "");
                        if (s.length() > 0) {
                            synonyms.add(s);
                        }
                    }
                }

                return DefinitionResult.success(definition, phonetic, synonyms);

            } catch (IOException e) {
                return DefinitionResult.failure(e.getMessage());
            } catch (JSONException e) {
                return DefinitionResult.failure("Bad response from server");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(DefinitionResult result) {
            progressBar.setVisibility(View.GONE);
            if (result.ok) {
                tvDefinition.setText(result.definition);

                if (result.phonetic != null && result.phonetic.length() > 0) {
                    tvPhonetic.setText(result.phonetic);
                    tvPhonetic.setVisibility(View.VISIBLE);
                }

                if (result.synonyms != null && !result.synonyms.isEmpty()) {
                    for (String synonym : result.synonyms) {
                        TextView chip = new TextView(MainActivity.this);
                        chip.setText(synonym);
                        chip.setTextColor(getResources().getColor(R.color.chip_text));
                        chip.setBackgroundResource(R.drawable.chip_background);
                        chip.setTextSize(14);
                        flowSynonyms.addView(chip);
                    }
                    synonymsDivider.setVisibility(View.VISIBLE);
                    synonymsHeader.setVisibility(View.VISIBLE);
                    flowSynonyms.setVisibility(View.VISIBLE);
                }
            } else {
                tvDefinition.setText(R.string.definition_unavailable);
                String message = getString(R.string.error_network)
                        + (result.error != null ? ": " + result.error : "");
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static class DefinitionResult {
        final boolean ok;
        final String definition;
        final String phonetic;
        final ArrayList<String> synonyms;
        final String error;

        private DefinitionResult(boolean ok, String definition, String phonetic,
                                  ArrayList<String> synonyms, String error) {
            this.ok = ok;
            this.definition = definition;
            this.phonetic = phonetic;
            this.synonyms = synonyms;
            this.error = error;
        }

        static DefinitionResult success(String definition, String phonetic, ArrayList<String> synonyms) {
            return new DefinitionResult(true, definition, phonetic, synonyms, null);
        }

        static DefinitionResult failure(String error) {
            return new DefinitionResult(false, null, null, null, error);
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
