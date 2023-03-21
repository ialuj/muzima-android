package com.muzima.utils;

import static com.muzima.utils.Constants.DataSyncServiceConstants.CONFIG_BEFORE_UPDATE;
import static com.muzima.utils.Constants.DataSyncServiceConstants.SYNC_DERIVED_CONCEPTS_AND_OBS_BASED_ON_CHANGES_IN_CONFIG;
import static com.muzima.utils.Constants.DataSyncServiceConstants.SYNC_TYPE;

import android.content.Context;

import com.muzima.api.model.SetupConfigurationTemplate;
import com.muzima.view.SyncIntent;

public class DownloadAndDeleteDerivedConceptAndObservationBasedOnConfigChangesIntent extends SyncIntent {
    public DownloadAndDeleteDerivedConceptAndObservationBasedOnConfigChangesIntent(Context context, SetupConfigurationTemplate configBeforeConfigUpdate) {
        super(context);
        putExtra(SYNC_TYPE, SYNC_DERIVED_CONCEPTS_AND_OBS_BASED_ON_CHANGES_IN_CONFIG);
        putExtra(CONFIG_BEFORE_UPDATE, configBeforeConfigUpdate);
    }
}
