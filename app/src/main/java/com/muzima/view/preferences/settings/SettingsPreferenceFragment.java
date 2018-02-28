/*
 * Copyright (c) 2014 - 2017. The Trustees of Indiana University, Moi University
 * and Vanderbilt University Medical Center.
 *
 * This version of the code is licensed under the MPL 2.0 Open Source license
 * with additional health care disclaimer.
 * If the user is an entity intending to commercialize any application that uses
 *  this code in a for-profit venture,please contact the copyright holder.
 */

package com.muzima.view.preferences.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;;
import android.widget.Toast;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.api.model.Location;
import com.muzima.controller.LocationController;
import com.muzima.scheduler.RealTimeFormUploader;
import com.muzima.service.MuzimaSyncService;
import com.muzima.service.RequireMedicalRecordNumberPreferenceService;
import com.muzima.tasks.ValidateURLTask;
import com.muzima.util.Constants;
import com.muzima.utils.NetworkUtils;
import com.muzima.utils.StringUtils;
import com.muzima.view.preferences.SettingsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.muzima.utils.Constants.DataSyncServiceConstants.SyncStatusConstants.SUCCESS;

public class SettingsPreferenceFragment extends PreferenceFragment  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private String serverPreferenceKey;
    private String usernamePreferenceKey;
    private String timeoutPreferenceKey;
    private String passwordPreferenceKey;
    private String autoSavePreferenceKey;
    private String realTimeSyncPreferenceKey;
    private String encounterProviderPreferenceKey;
    private String duplicateFormDataPreferenceKey;
    private String fontSizePreferenceKey;
    private String landingPagePreferenceKey;
    private String requireMedicalRecordNumberKey;

    private EditTextPreference serverPreference;
    private EditTextPreference usernamePreference;
    private EditTextPreference timeoutPreference;
    private EditTextPreference passwordPreference;
    private EditTextPreference autoSaveIntervalPreference;
    private CheckBoxPreference encounterProviderPreference;
    //Disabling duplicate form data preference until a better workflow of flagging duplicates is thought out. See MUZIMA-488
   // private CheckBoxPreference duplicateFormDataPreference;
    private CheckBoxPreference realTimeSyncPreference;
    private CheckBoxPreference requireMedicalRecordNumberPreference;
    private ListPreference fontSizePreference;
    private ListPreference landingPagePreference;

    private String newURL;
    private Map<String, SettingsPreferenceFragment.PreferenceChangeHandler> actions = new HashMap<String, SettingsPreferenceFragment.PreferenceChangeHandler>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);

        serverPreferenceKey = getResources().getString(R.string.preference_server);
        serverPreference = (EditTextPreference) getPreferenceScreen().findPreference(serverPreferenceKey);
        serverPreference.setSummary(serverPreference.getText());
        serverPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                newURL = newValue.toString();
                if (!serverPreference.getText().equalsIgnoreCase(newURL)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder
                            .setCancelable(true)
                            .setIcon(getResources().getDrawable(R.drawable.ic_warning))
                            .setTitle(getResources().getString(R.string.general_caution))
                            .setMessage(getResources().getString(R.string.warning_switch_server))
                            .setPositiveButton("Yes", positiveClickListener())
                            .setNegativeButton("No", null).create().show();
                }
                return false;
            }
        });
        usernamePreferenceKey = getResources().getString(R.string.preference_username);
        usernamePreference = (EditTextPreference) getPreferenceScreen().findPreference(usernamePreferenceKey);
        usernamePreference.setSummary(usernamePreference.getText());
        usernamePreference.setEnabled(false);
        usernamePreference.setSelectable(false);

        timeoutPreferenceKey = getResources().getString(R.string.preference_timeout);
        timeoutPreference = (EditTextPreference) getPreferenceScreen().findPreference(timeoutPreferenceKey);
        timeoutPreference.setSummary(timeoutPreference.getText());
        timeoutPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                Integer timeOutInMin = Integer.valueOf(o.toString());
                ((MuzimaApplication) getActivity().getApplication()).resetTimer(timeOutInMin);
                return true;
            }
        });

        autoSavePreferenceKey = getResources().getString(R.string.preference_auto_save_interval);
        autoSaveIntervalPreference = (EditTextPreference) getPreferenceScreen().findPreference(autoSavePreferenceKey);
        autoSaveIntervalPreference.setSummary(autoSaveIntervalPreference.getText());

        passwordPreferenceKey = getResources().getString(R.string.preference_password);
        passwordPreference = (EditTextPreference) getPreferenceScreen().findPreference(passwordPreferenceKey);
        if (passwordPreference.getText() != null) {
            passwordPreference.setSummary(passwordPreference.getText().replaceAll(".", "*"));
        }
        passwordPreference.setEnabled(false);
        passwordPreference.setSelectable(false);


        realTimeSyncPreferenceKey = getResources().getString(R.string.preference_real_time_sync);
        realTimeSyncPreference = (CheckBoxPreference) getPreferenceScreen().findPreference(realTimeSyncPreferenceKey);
        realTimeSyncPreference.setSummary(realTimeSyncPreference.getSummary());
        realTimeSyncPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (realTimeSyncPreference.isChecked()) {
                    RealTimeFormUploader.getInstance().uploadAllCompletedForms(getActivity().getApplicationContext());
                }
                return false;
            }
        });

        registerTextPreferenceChangeHandler(serverPreferenceKey, serverPreference);
        registerTextPreferenceChangeHandler(usernamePreferenceKey, usernamePreference);
        registerTextPreferenceChangeHandler(passwordPreferenceKey, passwordPreference);
        registerTextPreferenceChangeHandler(autoSavePreferenceKey, autoSaveIntervalPreference);
        registerTextPreferenceChangeHandler(timeoutPreferenceKey, timeoutPreference);
        registerCheckboxPreferenceChangeHandler(realTimeSyncPreferenceKey, realTimeSyncPreference);

        encounterProviderPreferenceKey = getResources().getString(R.string.preference_encounter_provider_key);
        encounterProviderPreference = (CheckBoxPreference) getPreferenceScreen().findPreference(encounterProviderPreferenceKey);
        encounterProviderPreference.setSummary(encounterProviderPreference.getSummary());

        encounterProviderPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (o.equals(Boolean.TRUE)) {
                    String loggedInUserSystemId = ((MuzimaApplication) getActivity().getApplication()).getAuthenticatedUser().getSystemId();
                    if (((MuzimaApplication) getActivity().getApplication()).getProviderController().getProviderBySystemId(loggedInUserSystemId) == null) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder
                                .setCancelable(true)
                                .setIcon(getResources().getDrawable(R.drawable.ic_warning))
                                .setTitle(getResources().getString(R.string.title_provider_not_set))
                                .setMessage(getResources().getString(R.string.hint_provider_not_set))
                                .setPositiveButton(getResources().getText(R.string.general_ok), null).create().show();
                        return false;
                    } else {
                        return true;
                    }
                }
                return true;
            }
        });

        /*
        duplicateFormDataPreferenceKey = getResources().getString(R.string.preference_duplicate_form_data_key);
        duplicateFormDataPreference = (CheckBoxPreference)getPreferenceScreen().findPreference(duplicateFormDataPreferenceKey);
        duplicateFormDataPreference.setSummary(duplicateFormDataPreference.getSummary());
        */

        ListPreference listPreferenceCategory = (ListPreference) findPreference(getResources().getString(R.string.preference_default_encounter_location));
        if (listPreferenceCategory != null) {

            LocationController locationController = ((MuzimaApplication) getActivity().getApplication()).getLocationController();
            List<Location> locations = new ArrayList<Location>();
            try {
                locations = locationController.getAllLocations();
            } catch (LocationController.LocationLoadException e) {
                e.printStackTrace( );
            }
            CharSequence entries[] = new String[locations.size()+1];
            CharSequence entryValues[] = new String[locations.size()+1];

            entries[0] = getResources().getString(R.string.no_default_encounter_location);
            entryValues[0] = getResources().getString(R.string.no_default_encounter_location);
            int i = 1;
            for (Location location : locations) {
                entries[i] = location.getName();
                entryValues[i] = Integer.toString(location.getId());
                i++;
            }
            listPreferenceCategory.setEntries(entries);
            listPreferenceCategory.setEntryValues(entryValues);
        }


        fontSizePreferenceKey = getResources().getString(R.string.preference_font_size);
        fontSizePreference = (ListPreference) getPreferenceScreen().findPreference(fontSizePreferenceKey);
        fontSizePreference.setSummary(fontSizePreference.getValue());
        registerListPreferenceChangeHandler(fontSizePreferenceKey, fontSizePreference);

        landingPagePreferenceKey = getResources().getString(R.string.preference_landing_page);
        landingPagePreference = (ListPreference) getPreferenceScreen().findPreference(landingPagePreferenceKey);
        landingPagePreference.setSummary(landingPagePreference.getValue());
        registerListPreferenceChangeHandler(landingPagePreferenceKey, landingPagePreference);

        requireMedicalRecordNumberKey = getResources().getString(R.string.preference_require_medical_record_number);
        requireMedicalRecordNumberPreference = (CheckBoxPreference) getPreferenceScreen().findPreference(requireMedicalRecordNumberKey);
        requireMedicalRecordNumberPreference.setSummary(requireMedicalRecordNumberPreference.getSummary());
        requireMedicalRecordNumberPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder
                    .setCancelable(true)
                    .setIcon(getResources().getDrawable(R.drawable.ic_refresh))
                    .setTitle(getString(R.string.title_setting_refresh))
                    .setMessage(getString(R.string.hint_setting_refresh))
                    .setPositiveButton(getResources().getText(R.string.general_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            new AsyncTask<Void, Void,int[] >() {
                                @Override
                                public int[] doInBackground(Void... params){
                                    MuzimaSyncService syncService = ((MuzimaApplication) getActivity()
                                            .getApplication()).getMuzimaSyncService();
                                    return syncService.downloadSetting(Constants.ServerSettings
                                            .PATIENT_IDENTIFIER_AUTOGENERATTION_SETTING);
                                }

                                @Override
                                protected void onPostExecute(int[] result) {
                                    if(result[0] == SUCCESS) {
                                        RequireMedicalRecordNumberPreferenceService requireMedicalRecordNumberPreferenceService
                                                = new RequireMedicalRecordNumberPreferenceService((MuzimaApplication) getActivity()
                                                .getApplication());
                                        requireMedicalRecordNumberPreferenceService.saveRequireMedicalRecordNumberPreference();
                                        requireMedicalRecordNumberPreference
                                                .setChecked(requireMedicalRecordNumberPreferenceService.getRequireMedicalRecordNumberPreferenceValue());
                                        Toast.makeText(getActivity(), getString(R.string.info_setting_download_success), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(getActivity(), getString(R.string.warning_setting_download_failure), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }.execute();
                        }
                    }).create().show();
                return false;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        resetEncounterProviderPreference();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (key.equalsIgnoreCase("wizardFinished") || key.equalsIgnoreCase("encounterProviderPreference")) {
            return;
        }
        PreferenceChangeHandler preferenceChangeHandler = actions.get(key);
        if(preferenceChangeHandler!=null) {
            preferenceChangeHandler.handle(sharedPreferences);
        }
    }

    private static interface PreferenceChangeHandler {
        public void handle(SharedPreferences sharedPreferences);
    }


    private void registerTextPreferenceChangeHandler(final String key, final EditTextPreference preference) {

        actions.put(key, new PreferenceChangeHandler() {
            @Override
            public void handle(SharedPreferences sharedPreferences) {
                preference.setSummary(sharedPreferences.getString(key, StringUtils.EMPTY));
            }
        });
    }

    private void registerListPreferenceChangeHandler(final String key, final ListPreference preference) {

        actions.put(key, new PreferenceChangeHandler() {
            @Override
            public void handle(SharedPreferences sharedPreferences) {
                preference.setSummary(sharedPreferences.getString(key, StringUtils.EMPTY));
            }
        });
    }

    private void registerCheckboxPreferenceChangeHandler(final String key, final CheckBoxPreference preference) {
        actions.put(key, new PreferenceChangeHandler() {
            @Override
            public void handle(SharedPreferences sharedPreferences) {
                preference.setChecked(sharedPreferences.getBoolean(key, false));
            }
        });
    }

    private Dialog.OnClickListener positiveClickListener() {
        return new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                changeServerURL(dialog);
            }

        };
    }

    private void changeServerURL(DialogInterface dialog) {
        dialog.dismiss();
        if (NetworkUtils.isConnectedToNetwork(getActivity())) {
            new ValidateURLTask(this).execute(newURL);
        }
    }

    private void resetEncounterProviderPreference() {
        if(encounterProviderPreference.isChecked()){
            String loggedInUserSystemId = ((MuzimaApplication) getActivity().getApplication()).getAuthenticatedUser().getSystemId();
            if (((MuzimaApplication) getActivity().getApplication()).getProviderController().getProviderBySystemId(loggedInUserSystemId) == null){
                encounterProviderPreference.setChecked(false);
            }
        }
    }

    public void validationURLResult(boolean result) {
        if (result) {
            new SyncFormDataTask(this).execute();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder
                    .setCancelable(true)
                    .setIcon(getResources().getDrawable(R.drawable.ic_warning))
                    .setTitle("Invalid")
                    .setMessage("The URL you have provided is invalid")
                    .setPositiveButton("Ok", null);
            builder.create().show();
        }
    }

    public void syncedFormData(boolean result) {
        if (result) {
            new ResetDataTask((SettingsActivity)getActivity(), newURL).execute();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder
                    .setCancelable(true)
                    .setIcon(getResources().getDrawable(R.drawable.ic_warning))
                    .setTitle("Failure")
                    .setMessage(getString(R.string.info_form_data_upload_fail))
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new ResetDataTask((SettingsActivity)getActivity(), newURL).execute();
                        }
                    })
                    .setNegativeButton("No", null);
            builder.create().show();
        }
    }
}
