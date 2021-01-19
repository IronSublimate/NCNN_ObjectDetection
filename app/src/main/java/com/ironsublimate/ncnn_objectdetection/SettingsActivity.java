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

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private static String TAG = "SettingsActivity";
    private static ListPreference listPreference = null;
    private static SwitchPreferenceCompat useGPU = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            listPreference = manager.findPreference("reply");
            useGPU = manager.findPreference(this.getString(R.string.useGPU));
            if (listPreference == null || useGPU == null) {
                Log.e(TAG, "Cannot find Preference");
                return;
            }
//            listPreference.setEntries();
//            String s=listPreference.getValue();
//            Log.i(TAG,s);
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
            returnMain();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        returnMain();
        super.onBackPressed();
    }
//    @Override
//    protected void onDestroy(){
//        Intent intent=new Intent();
//        intent.putExtra(this.getResources().getString(R.string.useGPU),useGPU.isChecked());
//        intent.putExtra(this.getResources().getString(R.string.method_index),listPreference.findIndexOfValue(listPreference.getValue()));
//        this.setResult(RESULT_OK,intent);
//        super.onDestroy();
//    }
}