package com.muzima.view;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.adapters.MainDashboardAdapter;
import com.muzima.adapters.cohort.CohortFilterAdapter;
import com.muzima.api.model.Cohort;
import com.muzima.api.model.Form;
import com.muzima.api.model.Patient;
import com.muzima.api.model.PatientIdentifier;
import com.muzima.api.model.SmartCardRecord;
import com.muzima.api.model.User;
import com.muzima.api.service.SmartCardRecordService;
import com.muzima.controller.CohortController;
import com.muzima.controller.FormController;
import com.muzima.controller.NotificationController;
import com.muzima.controller.PatientController;
import com.muzima.domain.Credentials;
import com.muzima.model.CohortFilter;
import com.muzima.model.cohort.CohortItem;
import com.muzima.model.events.BottomSheetToggleEvent;
import com.muzima.model.events.CloseBottomSheetEvent;
import com.muzima.model.events.CohortFilterActionEvent;
import com.muzima.model.events.CohortsActionModeEvent;
import com.muzima.model.events.DestroyActionModeEvent;
import com.muzima.model.events.FormFilterBottomSheetClosedEvent;
import com.muzima.model.events.FormSortEvent;
import com.muzima.model.events.FormsActionModeEvent;
import com.muzima.model.events.ShowCohortFilterEvent;
import com.muzima.model.events.ShowFormsFilterEvent;
import com.muzima.scheduler.MuzimaJobScheduleBuilder;
import com.muzima.scheduler.RealTimeFormUploader;
import com.muzima.service.WizardFinishPreferenceService;
import com.muzima.tasks.DownloadCohortsTask;
import com.muzima.tasks.DownloadFormsTask;
import com.muzima.tasks.LoadDownloadedCohortsTask;
import com.muzima.utils.Constants;
import com.muzima.utils.LanguageUtil;
import com.muzima.utils.MuzimaPreferences;
import com.muzima.utils.ThemeUtils;
import com.muzima.utils.smartcard.KenyaEmrShrMapper;
import com.muzima.utils.smartcard.SmartCardIntentIntegrator;
import com.muzima.utils.smartcard.SmartCardIntentResult;
import com.muzima.view.barcode.BarcodeCaptureActivity;
import com.muzima.view.patients.PatientsLocationMapActivity;
import com.muzima.view.preferences.SettingsActivity;

import org.apache.lucene.queryParser.ParseException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.muzima.utils.Constants.NotificationStatusConstants.NOTIFICATION_UNREAD;
import static com.muzima.utils.smartcard.SmartCardIntentIntegrator.SMARTCARD_READ_REQUEST_CODE;

public class MainDashboardActivity extends BaseFragmentActivity implements NavigationView.OnNavigationItemSelectedListener, CohortFilterAdapter.CohortFilterClickedListener {
    private static final int RC_BARCODE_CAPTURE = 9001;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private ViewPager viewPager;
    private TextView headerTitleTextView;
    private MainDashboardAdapter adapter;
    private BottomNavigationView bottomNavigationView;
    private ActionBarDrawerToggle drawerToggle;
    private final ThemeUtils themeUtils = new ThemeUtils();
    private final LanguageUtil languageUtil = new LanguageUtil();
    private MenuItem menuLocation;
    private MenuItem menuRefresh;
    private ActionMode.Callback actionModeCallback;
    private ActionMode actionMode;
    private Credentials credentials;
    private BackgroundQueryTask mBackgroundQueryTask;
    private MenuItem loadingMenuItem;
    private BottomSheetBehavior cohortFilterBottomSheetBehavior;
    private View cohortFilterBottomSheetView;
    private BottomSheetBehavior formFilterBottomSheetBehavior;
    private View formFilterBottomSheetView;
    private View closeBottomSheet;
    private View closeFormsBottomSheetView;
    private View formFilterStatusContainer;
    private CheckBox formFilterStatusCheckbox;
    private View formFilterNamesContainer;
    private CheckBox formFilterNamesCheckbox;
    private CohortFilterAdapter cohortFilterAdapter;
    private RecyclerView filterOptionsRecyclerView;
    private List<CohortItem> selectedCohorts = new ArrayList<>();
    private List<Form> selectedForms = new ArrayList<>();
    private List<CohortFilter> cohortList = new ArrayList<>();
    private List<CohortFilter> selectedCohortFilters = new ArrayList<>();
    private int selectedCohortsCount = 0;
    private SmartCardRecordService smartCardService;
    private SmartCardRecord smartCardRecord;
    private Patient SHRPatient;
    private Patient SHRToMuzimaMatchingPatient;
    private int selectionDifference;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        themeUtils.onCreate(MainDashboardActivity.this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_root_layout);
        RealTimeFormUploader.getInstance().uploadAllCompletedForms(getApplicationContext(), false);
        initializeResources();
        loadCohorts(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard_home, menu);
        menuLocation = menu.findItem(R.id.menu_location);
        menuRefresh = menu.findItem(R.id.menu_load);
        menuLocation.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.general_launching_map_message), Toast.LENGTH_SHORT).show();
                navigateToClientsLocationMap();
                return true;
            }
        });

        menuRefresh.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.info_muzima_sync_service_in_progress), Toast.LENGTH_LONG).show();
                new MuzimaJobScheduleBuilder(getApplicationContext()).schedulePeriodicBackgroundJob(1000, true);
                return true;
            }
        });
        return true;
    }

    private void loadCohorts(final boolean showFilter) {
        ((MuzimaApplication) getApplicationContext()).getExecutorService()
                .execute(new LoadDownloadedCohortsTask(getApplicationContext(), new LoadDownloadedCohortsTask.OnDownloadedCohortsLoadedCallback() {
                    @Override
                    public void onCohortsLoaded(final List<Cohort> cohorts) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                cohortList.clear();
                                Log.e(getClass().getSimpleName(),"Debugging size is === "+selectedCohortFilters.size());
                                if (selectedCohortFilters.size()==0)
                                    cohortList.add(new CohortFilter(null, true));
                                else if(selectedCohortFilters.size()==1 && selectedCohortFilters.get(0).getCohort()==null)
                                    cohortList.add(new CohortFilter(null, true));
                                else
                                    cohortList.add(new CohortFilter(null, false));
                                for (Cohort cohort : cohorts) {
                                    boolean isCohortSeleted = false;
                                    for(CohortFilter cohortFilter : selectedCohortFilters){
                                        if(cohortFilter.getCohort() != null) {
                                            if (cohortFilter.getCohort().getUuid().equals(cohort.getUuid())) {
                                                isCohortSeleted = true;
                                            }
                                        }
                                    }
                                    cohortList.add(new CohortFilter(cohort, isCohortSeleted));
                                }
                                cohortFilterAdapter.notifyDataSetChanged();
                                if (showFilter)
                                    cohortFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                            }
                        });
                    }
                }));
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            EventBus.getDefault().register(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Subscribe
    public void closeBottomSheetEvent(CloseBottomSheetEvent event) {
        cohortFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    @Subscribe
    public void showCohortFilterEvent(ShowCohortFilterEvent event) {
        loadCohorts(true);
    }

    @Subscribe
    public void onCohortDownloadActionModeEvent(CohortsActionModeEvent actionModeEvent) {
        selectedCohorts = actionModeEvent.getSelectedCohorts();
        initActionMode(Constants.ACTION_MODE_EVENT.COHORTS_DOWNLOAD_ACTION);
    }

    @Subscribe
    public void onFormsDownloadActionModeEvent(FormsActionModeEvent actionModeEvent) {
        selectedForms = actionModeEvent.getSelectedFormsList();
        initActionMode(Constants.ACTION_MODE_EVENT.FORMS_DOWNLOAD_ACTION);
    }

    private void initActionMode(final int action) {
        selectedCohortsCount = 0;
        actionModeCallback = new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                getMenuInflater().inflate(R.menu.menu_cohort_actions, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                loadingMenuItem = menu.findItem(R.id.menu_downloading_action);
                return true;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.menu_download_action) {
                    loadingMenuItem.setActionView(new ProgressBar(MainDashboardActivity.this));
                    loadingMenuItem.setVisible(true);
                    menuItem.setVisible(false);
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.info_muzima_sync_service_in_progress), Toast.LENGTH_LONG).show();
                    if (action == Constants.ACTION_MODE_EVENT.COHORTS_DOWNLOAD_ACTION) {
                        ((MuzimaApplication) getApplicationContext()).getExecutorService()
                                .execute(new DownloadCohortsTask(getApplicationContext(), selectedCohorts, new DownloadCohortsTask.CohortDownloadCallback() {
                                    @Override
                                    public void callbackDownload() {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                actionMode.finish();
                                                loadingMenuItem.setVisible(false);
                                                EventBus.getDefault().post(new DestroyActionModeEvent());
                                                Toast.makeText(getApplicationContext(), getResources().getString(R.string.info_muzima_sync_service_finish), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }));
                    } else if (action == Constants.ACTION_MODE_EVENT.FORMS_DOWNLOAD_ACTION) {
                        ((MuzimaApplication) getApplicationContext()).getExecutorService()
                                .execute(new DownloadFormsTask(getApplicationContext(), selectedForms, new DownloadFormsTask.FormsDownloadCallback() {
                                    @Override
                                    public void formsDownloadFinished() {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                selectedForms.clear();
                                                actionMode.finish();
                                                loadingMenuItem.setVisible(false);
                                                EventBus.getDefault().post(new DestroyActionModeEvent());
                                                Toast.makeText(getApplicationContext(), getResources().getString(R.string.info_muzima_sync_service_finish), Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }));
                    }
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                if (selectionDifference == selectedCohortsCount)
                    EventBus.getDefault().post(new DestroyActionModeEvent());
                else
                    selectionDifference = selectedCohortsCount;
            }
        };

        for (CohortItem selectedCohort : selectedCohorts) {
            if (selectedCohort.isSelected()) selectedCohortsCount = selectedCohortsCount + 1;
        }

        actionMode = startActionMode(actionModeCallback);

        if (action == Constants.ACTION_MODE_EVENT.COHORTS_DOWNLOAD_ACTION) {
            if (selectedCohortsCount < 1) actionMode.finish();
            actionMode.setTitle(String.format(Locale.getDefault(), "%d %s", selectedCohortsCount, getResources().getString(R.string.general_selected)));
        } else if (action == Constants.ACTION_MODE_EVENT.FORMS_DOWNLOAD_ACTION) {
            if (selectedForms.size() < 1) actionMode.finish();
            actionMode.setTitle(String.format(Locale.getDefault(), "%d %s", selectedForms.size(), getResources().getString(R.string.general_selected)));
        }
    }

    public void hideProgressbar() {
        menuRefresh.setActionView(null);
    }

    public void showProgressBar() {
        menuRefresh.setActionView(R.layout.refresh_menuitem);
    }

    private void initializeResources() {
        viewPager = findViewById(R.id.main_dashboard_view_pager);
        bottomNavigationView = findViewById(R.id.main_dashboard_bottom_navigation);
        toolbar = findViewById(R.id.dashboard_toolbar);
        drawerLayout = findViewById(R.id.main_dashboard_drawer_layout);
        navigationView = findViewById(R.id.dashboard_navigation);
        cohortFilterBottomSheetView = findViewById(R.id.dashboard_home_bottom_view_container);
        cohortFilterBottomSheetBehavior = BottomSheetBehavior.from(cohortFilterBottomSheetView);
        closeBottomSheet = findViewById(R.id.bottom_sheet_close_view);
        filterOptionsRecyclerView = findViewById(R.id.dashboard_home_filter_recycler_view);
        formFilterBottomSheetView = findViewById(R.id.dashboard_home_form_bottom_view_container);
        formFilterBottomSheetBehavior = BottomSheetBehavior.from(formFilterBottomSheetView);
        formFilterNamesContainer = findViewById(R.id.form_filter_by_name_container);
        formFilterNamesCheckbox = findViewById(R.id.form_filter_name_checkbox);
        formFilterStatusContainer = findViewById(R.id.form_filter_by_status_container);
        formFilterStatusCheckbox = findViewById(R.id.form_filter_status_checkbox);
        closeFormsBottomSheetView = findViewById(R.id.forms_bottom_sheet_close_view);
        cohortFilterAdapter = new CohortFilterAdapter(getApplicationContext(), cohortList, this);
        filterOptionsRecyclerView.setAdapter(cohortFilterAdapter);
        filterOptionsRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        headerTitleTextView = navigationView.getHeaderView(0).findViewById(R.id.dashboard_header_title_text_view);

        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(MainDashboardActivity.this, drawerLayout,
                toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        adapter = new MainDashboardAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        credentials = new Credentials(this);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //disable event propagation for swipe action
                if (viewPager.getCurrentItem() != 0)
                    viewPager.setCurrentItem(10);
                else if (viewPager.getCurrentItem() == 0)
                    viewPager.setCurrentItem(-1);
                else if (viewPager.getCurrentItem() == 1){
                    viewPager.setCurrentItem(0);
                    viewPager.setCurrentItem(1);
                }
                return true;
            }

        });

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                menuItem.setChecked(true);
                menuItem.setEnabled(true);
                if (menuItem.getItemId() == R.id.main_dashboard_home_menu) {
                    viewPager.setCurrentItem(0);
                    if (menuLocation != null)
                        menuLocation.setVisible(true);
                } else if (menuItem.getItemId() == R.id.main_dashboard_cohorts_menu) {
                    viewPager.setCurrentItem(1);
                    if (menuLocation != null)
                        menuLocation.setVisible(false);
                } else if (menuItem.getItemId() == R.id.main_dashboard_forms_menu) {
                    viewPager.setCurrentItem(2);
                    if (menuLocation != null)
                        menuLocation.setVisible(false);
                }
                return false;
            }
        });

        closeBottomSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cohortFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        cohortFilterBottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    EventBus.getDefault().post(new CohortFilterActionEvent(selectedCohortFilters, false));
                }
                EventBus.getDefault().post(new BottomSheetToggleEvent(newState));
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        closeFormsBottomSheetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        formFilterStatusCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                formFilterNamesCheckbox.setChecked(false);
                formFilterStatusContainer.setBackground(getResources().getDrawable(R.drawable.global_highlight_background));
                if (MuzimaPreferences.getIsLightModeThemeSelectedPreference(getApplicationContext()))
                    formFilterNamesContainer.setBackgroundColor(getResources().getColor(R.color.primary_white));
                else
                    formFilterNamesContainer.setBackgroundColor(getResources().getColor(R.color.primary_black));
                EventBus.getDefault().post(new FormSortEvent(Constants.FORM_SORT_STRATEGY.SORT_BY_STATUS));
                formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        formFilterNamesCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                formFilterStatusCheckbox.setChecked(false);
                formFilterNamesContainer.setBackground(getResources().getDrawable(R.drawable.global_highlight_background));
                if (MuzimaPreferences.getIsLightModeThemeSelectedPreference(getApplicationContext()))
                    formFilterStatusContainer.setBackgroundColor(getResources().getColor(R.color.primary_white));
                else
                    formFilterStatusContainer.setBackgroundColor(getResources().getColor(R.color.primary_black));
                EventBus.getDefault().post(new FormSortEvent(Constants.FORM_SORT_STRATEGY.SORT_BY_NAME));
                formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });
        formFilterBottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    EventBus.getDefault().post(new FormFilterBottomSheetClosedEvent(true));
                } else {
                    EventBus.getDefault().post(new FormFilterBottomSheetClosedEvent(false));
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

        cohortFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        headerTitleTextView.setText(((MuzimaApplication) getApplicationContext()).getAuthenticatedUser().getUsername());

        setTitle(" ");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        super.onActivityResult(requestCode, resultCode, dataIntent);
        switch (requestCode) {
            case SMARTCARD_READ_REQUEST_CODE:
                processSmartCardReadResult(requestCode, resultCode, dataIntent);
                break;
            case RC_BARCODE_CAPTURE:
                Intent intent;
                intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
                intent.putExtra(BarcodeCaptureActivity.AutoFocus, true);
                intent.putExtra(BarcodeCaptureActivity.UseFlash, false);

                startActivityForResult(intent, RC_BARCODE_CAPTURE);
                break;
            default:
                break;
        }
    }

    private void readSmartCard() {
        SmartCardIntentIntegrator SHRIntegrator = new SmartCardIntentIntegrator(this);
        SHRIntegrator.initiateCardRead();
        Toast.makeText(getApplicationContext(), "Opening Card Reader", Toast.LENGTH_LONG).show();
    }

    private void processSmartCardReadResult(int requestCode, int resultCode, Intent dataIntent) {
        SmartCardIntentResult cardReadIntentResult = null;

        try {
            cardReadIntentResult = SmartCardIntentIntegrator.parseActivityResult(requestCode, resultCode, dataIntent);
        } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Could not get result", e);
        }
        if (cardReadIntentResult == null) {
            Toast.makeText(getApplicationContext(), "Card Read Failed", Toast.LENGTH_LONG).show();
            return;
        }

        if (cardReadIntentResult.isSuccessResult()) {
            smartCardRecord = cardReadIntentResult.getSmartCardRecord();
            if (smartCardRecord != null) {
                String SHRPayload = smartCardRecord.getPlainPayload();
                if (!SHRPayload.equals("") && !SHRPayload.isEmpty()) {
                    try {
                        SHRPatient = KenyaEmrShrMapper.extractPatientFromSHRModel(((MuzimaApplication) getApplicationContext()), SHRPayload);
                        if (SHRPatient != null) {
                            PatientIdentifier cardNumberIdentifier = SHRPatient.getIdentifier(Constants.Shr.KenyaEmr.PersonIdentifierType.CARD_SERIAL_NUMBER.name);

                            SHRToMuzimaMatchingPatient = null;

                            if (cardNumberIdentifier == null) {
                                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                                alertBuilder.setMessage("Could not find Card Serial number in shared health record")
                                        .setCancelable(true).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Searching Patient Locally", Toast.LENGTH_LONG).show();
//                                prepareRegisterLocallyDialog();
//                                prepareLocalSearchNotifyDialog(SHRPatient);
//                                executeLocalPatientSearchInBackgroundTask();
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), "This card seems to be blank", Toast.LENGTH_LONG).show();
                        }
                    } catch (KenyaEmrShrMapper.ShrParseException e) {
                        Log.e("EMR_IN", "EMR Error ", e);
                    }
                }
            }
        } else {
            Snackbar.make(findViewById(R.id.patient_lists_layout), getResources().getString(R.string.general_card_read_failed_msg) + cardReadIntentResult.getErrors(), Snackbar.LENGTH_LONG)
                    .setAction(getResources().getString(R.string.general_retry), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            readSmartCard();
                        }
                    }).show();
        }
    }

    private void navigateToClientsLocationMap() {
        Intent intent = new Intent(getApplicationContext(), PatientsLocationMapActivity.class);
        startActivity(intent);
    }

    @Subscribe
    public void showFormsFilterBottomSheetEvent(ShowFormsFilterEvent event) {
        if (event.isCloseAction()) {
            formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        } else {
            formFilterBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

    }

    @Override
    public void onUserInteraction() {
        ((MuzimaApplication) getApplication()).restartTimer();
        super.onUserInteraction();
    }

    @Override
    public void onCohortFilterClicked(int position) {
        List<CohortFilter> cfilter = new ArrayList<>(selectedCohortFilters);
        CohortFilter cohortFilter = cohortList.get(position);
        if (cohortFilter.getCohort() == null) {
            if (cohortFilter.isSelected()) {
                cohortFilter.setSelected(false);
            } else {
                cohortFilter.setSelected(true);
                for (CohortFilter filter : cohortList) {
                    if (filter.getCohort() != null) {
                        filter.setSelected(false);
                        for (CohortFilter cf : cfilter){
                            if(cf.getCohort() != null) {
                                if (filter.getCohort().getUuid().equals(cf.getCohort().getUuid())) {
                                    selectedCohortFilters.remove(cf);
                                }
                            }
                        }
                    }
                }
                selectedCohortFilters.add(cohortFilter);
            }
        } else {
            if (cohortFilter.isSelected()) {
                for (CohortFilter cf : cfilter){
                    if(cf.getCohort() != null && cohortFilter.getCohort() != null) {
                        if (cf.getCohort().getUuid().equals(cohortFilter.getCohort().getUuid())) {
                            selectedCohortFilters.remove(cf);
                        }
                    }
                }
                cohortFilter.setSelected(false);
                markAllClientsCohortFilter(selectedCohortFilters.isEmpty());
            } else {
                cohortFilter.setSelected(true);
                for (CohortFilter cf : cfilter){
                    if(cf.getCohort() == null) {
                        selectedCohortFilters.remove(cf);
                    }
                }
                selectedCohortFilters.add(cohortFilter);
                markAllClientsCohortFilter(false);
            }
        }

        cohortFilterAdapter.notifyDataSetChanged();
    }

    private void markAllClientsCohortFilter(boolean b) {
        for (CohortFilter filter : cohortList) {
            if (filter.getCohort() == null)
                filter.setSelected(b);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        themeUtils.onResume(this);
        languageUtil.onResume(this);
        showIncompleteWizardWarning();
        executeBackgroundTask();
    }

    private void executeBackgroundTask() {
        mBackgroundQueryTask = new BackgroundQueryTask();
        mBackgroundQueryTask.execute();
    }

    private void showIncompleteWizardWarning() {
        if (!new WizardFinishPreferenceService(this).isWizardFinished()) {
            if (checkIfDisclaimerIsAccepted()) {
                Toast.makeText(getApplicationContext(), getString(R.string.error_wizard_interrupted), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    private boolean checkIfDisclaimerIsAccepted() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String disclaimerKey = getResources().getString(R.string.preference_disclaimer);
        return settings.getBoolean(disclaimerKey, false);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        Intent intent;

        if (menuItem.getItemId() == R.id.drawer_menu_home) {
            intent = new Intent(getApplicationContext(), MainDashboardActivity.class);
            startActivity(intent);
            finish();
        } else if (menuItem.getItemId() == R.id.drawer_menu_settings) {
            intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(intent);
            finish();
        } else if (menuItem.getItemId() == R.id.drawer_menu_help) {
            intent = new Intent(getApplicationContext(), HelpActivity.class);
            startActivity(intent);
            finish();
        } else if (menuItem.getItemId() == R.id.drawer_menu_feedback) {
            intent = new Intent(getApplicationContext(), FeedbackActivity.class);
            startActivity(intent);
            finish();
        } else if (menuItem.getItemId() == R.id.drawer_menu_contact_us) {
            intent = new Intent(getApplicationContext(), FeedbackActivity.class);
            startActivity(intent);
            finish();
        } else if (menuItem.getItemId() == R.id.drawer_menu_logout) {
            finishAffinity();
        }
        return false;
    }

    private static class HomeActivityMetadata {
        int totalCohorts;
        int syncedCohorts;
        int syncedPatients;
        int incompleteForms;
        int completeAndUnsyncedForms;
        int newNotifications;
        int totalNotifications;
        boolean isCohortUpdateAvailable;
    }

    class BackgroundQueryTask extends AsyncTask<Void, Void, HomeActivityMetadata> {

        @Override
        protected HomeActivityMetadata doInBackground(Void... voids) {
            MuzimaApplication muzimaApplication = (MuzimaApplication) getApplication();
            HomeActivityMetadata homeActivityMetadata = new HomeActivityMetadata();
            CohortController cohortController = muzimaApplication.getCohortController();
            PatientController patientController = muzimaApplication.getPatientController();
            FormController formController = muzimaApplication.getFormController();
            NotificationController notificationController = muzimaApplication.getNotificationController();
            try {
                homeActivityMetadata.totalCohorts = cohortController.countAllCohorts();
                homeActivityMetadata.syncedCohorts = cohortController.countSyncedCohorts();
                homeActivityMetadata.isCohortUpdateAvailable = cohortController.isUpdateAvailable();
                homeActivityMetadata.syncedPatients = patientController.countAllPatients();
                homeActivityMetadata.incompleteForms = formController.countAllIncompleteForms();
                homeActivityMetadata.completeAndUnsyncedForms = formController.countAllCompleteForms();

                // Notifications
                User authenticatedUser = ((MuzimaApplication) getApplicationContext()).getAuthenticatedUser();
                if (authenticatedUser != null) {
                    homeActivityMetadata.newNotifications = notificationController
                            .getAllNotificationsByReceiverCount(authenticatedUser.getPerson().getUuid(), NOTIFICATION_UNREAD);
                    homeActivityMetadata.totalNotifications = notificationController
                            .getAllNotificationsByReceiverCount(authenticatedUser.getPerson().getUuid(), null);
                } else {
                    homeActivityMetadata.newNotifications = 0;
                    homeActivityMetadata.totalNotifications = 0;
                }
            } catch (CohortController.CohortFetchException e) {
                Log.w(getClass().getSimpleName(), "CohortFetchException occurred while fetching metadata in MainActivityBackgroundTask", e);
            } catch (PatientController.PatientLoadException e) {
                Log.w(getClass().getSimpleName(), "PatientLoadException occurred while fetching metadata in MainActivityBackgroundTask", e);
            } catch (FormController.FormFetchException e) {
                Log.w(getClass().getSimpleName(), "FormFetchException occurred while fetching metadata in MainActivityBackgroundTask", e);
            } catch (NotificationController.NotificationFetchException e) {
                Log.w(getClass().getSimpleName(), "NotificationFetchException occurred while fetching metadata in MainActivityBackgroundTask", e);
            } catch (ParseException e) {
                Log.w(getClass().getSimpleName(), "ParseException occurred while fetching metadata in MainActivityBackgroundTask", e);
            }
            return homeActivityMetadata;
        }

        @Override
        protected void onPostExecute(HomeActivityMetadata homeActivityMetadata) {
//            ImageView cortUpdateAvailable = (ImageView) findViewById(R.id.pendingUpdateImg);
//            if (homeActivityMetadata.isCohortUpdateAvailable) {
//                cortUpdateAvailable.setVisibility(View.VISIBLE);
//            } else {
//                cortUpdateAvailable.setVisibility(View.GONE);
//            }
//
//            TextView patientDescriptionView = findViewById(R.id.patientDescription);
//            patientDescriptionView.setText(getString(R.string.hint_dashboard_clients_description,
//                    homeActivityMetadata.syncedPatients));
//
//            TextView formsDescription = findViewById(R.id.formDescription);
//            formsDescription.setText(getString(R.string.hint_dashboard_forms_description,
//                    homeActivityMetadata.incompleteForms, homeActivityMetadata.completeAndUnsyncedForms));
//
//            TextView notificationsDescription = findViewById(R.id.notificationDescription);
//            notificationsDescription.setText(getString(R.string.hint_dashboard_notifications_description,
//                    homeActivityMetadata.newNotifications, homeActivityMetadata.totalNotifications));

//            TextView currentUser = findViewById(R.id.currentUser);
//            currentUser.setText(getResources().getString(R.string.general_welcome) + " " + credentials.getUserName());
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 2)
            bottomNavigationView.setSelectedItemId(R.id.main_dashboard_cohorts_menu);
        else if (viewPager.getCurrentItem() == 1)
            bottomNavigationView.setSelectedItemId(R.id.main_dashboard_home_menu);
        else {
            showExitAlertDialog();
        }
    }

    private void showExitAlertDialog() {
        new AlertDialog.Builder(MainDashboardActivity.this)
                .setCancelable(true)
                .setIcon(themeUtils.getIconWarning(this))
                .setTitle(getResources().getString(R.string.title_logout_confirm))
                .setMessage(getResources().getString(R.string.warning_logout_confirm))
                .setPositiveButton(getString(R.string.general_yes), exitApplication())
                .setNegativeButton(getString(R.string.general_no), null)
                .create()
                .show();
    }

    private Dialog.OnClickListener exitApplication() {
        return new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((MuzimaApplication) getApplication()).logOut();
                finish();
                System.exit(0);
            }
        };
    }
}