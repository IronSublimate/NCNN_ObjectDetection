package com.ironsublimate.ncnn_objectdetection;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.*;

import java.util.HashMap;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";
    public static final String KEY_SWITCH_USE_GPU = "switch_useGPU";
    private static ListPreference listPreference = null;
    private static SwitchPreferenceCompat useGPU = null;

    private static HashMap<String, String> methodMap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        methodMap = (HashMap<String, String>) (this.getIntent().getSerializableExtra(MainActivity.detectMethodsIntentName));
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
//        Switch switch_useGPU=this.findViewById(R.id.switch_useGPU);
//        View listPreference=this.findViewById(R.id.list_method);


    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            PreferenceManager manager = getPreferenceManager();
            ListPreference listPreference = manager.findPreference(this.getResources().getString(R.string.method_index));
            listPreference.setEntries(methodMap.values().toArray(new String[0]));
            listPreference.setEntryValues(methodMap.keySet().toArray(new String[0]));
        }
    }

    private void returnMain() {
        Intent intent = new Intent();
        intent.putExtra(this.getResources().getString(R.string.useGPU), useGPU.isChecked());
        intent.putExtra(this.getResources().getString(R.string.method_index), listPreference.findIndexOfValue(listPreference.getValue()));
        this.setResult(RESULT_OK, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            //returnMain();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        //returnMain();
        super.onBackPressed();
    }
}