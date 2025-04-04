# SmartHat App - Data Filtering Implementation

This document outlines the data filtering functionality implemented in the SmartHat Android application for filtering threshold breach data by date range.

## Table of Contents
1. [Data Models](#data-models)
2. [Utility Helpers](#utility-helpers)
3. [Database Implementation](#database-implementation)
4. [UI Implementation](#ui-implementation)
5. [Filter Application](#filter-application)

## Data Models

### DataFilter Model
The `DataFilter` class defines the structure of a date range filter:

```java
package com.team12.smarthat.models;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataFilter {
    public static final String TIMESTAMP_DATE_FORMAT = "MM/dd/yy HH:mm";

    private Date startDate;
    private Date endDate;

    public DataFilter() {
        this.startDate = null;
        this.endDate = null;
    }

    public DataFilter(Date startDate, Date endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public DataFilter(String startDateString, String endDateString) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TIMESTAMP_DATE_FORMAT, Locale.getDefault());
        simpleDateFormat.setLenient(false);

        this.startDate = simpleDateFormat.parse(startDateString);
        this.endDate = simpleDateFormat.parse(endDateString);
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public long getStartTimestamp() {
        return startDate.getTime();
    }

    public long getEndTimestamp() {
        return endDate.getTime();
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}
```

## Utility Helpers

### DataFilterHelper
The `DataFilterHelper` class implements a singleton pattern to manage filter state and persistence:

```java
package com.team12.smarthat.utils;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import com.team12.smarthat.models.DataFilter;

import java.text.ParseException;

public class DataFilterHelper {
    private static DataFilterHelper instance;
    private DataFilter currentFilter;

    private DataFilterHelper() {
        currentFilter = null;
    }

    public static DataFilterHelper getInstance() {
        if(instance == null) {
            instance = new DataFilterHelper();
        }

        return instance;
    }

    public void setFilter(DataFilter dataFilter) {
        currentFilter = dataFilter;
    }

    public DataFilter getCurrentFilter() {
        return currentFilter;
    }

    // Save filter state to sharedPreferences
    public void saveFilterState(Context context) {
        if(currentFilter != null) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
            SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
            prefsEditor.putString(Constants.PREF_FILTER_START_DATE, currentFilter.getStartDate().toString());
            prefsEditor.putString(Constants.PREF_FILTER_END_DATE, currentFilter.getEndDate().toString());

            prefsEditor.apply();
        }
    }

    // Retrieve filter state from sharedPreferences
    public void restoreFilterPreferences(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        String startDate = sharedPreferences.getString(Constants.PREF_FILTER_START_DATE, "");
        String endDate = sharedPreferences.getString(Constants.PREF_FILTER_END_DATE, "");

        if(!(startDate.isEmpty() || endDate.isEmpty())) {
            try {
                currentFilter = new DataFilter(startDate, endDate);;
            } catch(ParseException e) {
                currentFilter = null;
            }
        }
    }

    // Clear filters
    public void clearFilters() {
        this.currentFilter = null;
    }
}
```

## Database Implementation

### Database Query Logic
The `DatabaseHelper` class contains the logic for retrieving filtered data:

```java
// get all threshold breaches using filters
public LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold) {
    if(DataFilterHelper.getInstance().getCurrentFilter() != null) {
        long startTimeStamp = DataFilterHelper.getInstance().getCurrentFilter().getStartTimestamp();
        long endTimeStamp = DataFilterHelper.getInstance().getCurrentFilter().getEndTimestamp();
        Log.d(Constants.TAG_DATABASE, "Retrieving filtered data");
        return dao.getThresholdBreaches(dustThreshold, noiseThreshold, startTimeStamp, endTimeStamp);
    }
    else {
        Log.d(Constants.TAG_DATABASE, "Retrieving unfiltered data");
        return dao.getThresholdBreaches(dustThreshold, noiseThreshold);
    }
}
```

### DAO Implementation
The `SensorDataDao` interface defines the SQL queries with and without timestamp filters:

```java
// get all threshold breaches
@Query("SELECT * FROM sensor_data WHERE (sensorType = 'dust' AND value > :dustThreshold) OR (sensorType = 'noise' AND value > :noiseThreshold) ORDER BY timestamp DESC")
LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold);

// get all threshold breaches within a specified time frame
@Query("SELECT * FROM sensor_data WHERE ((sensorType = 'dust' AND value > :dustThreshold) " +
        "OR (sensorType = 'noise' AND value > :noiseThreshold)) " +
        "AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp " +
        "ORDER BY timestamp DESC")
LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold, long startTimestamp, long endTimestamp);
```

## UI Implementation

### DataFilterFragment
The `DataFilterFragment` class provides the UI for setting date filters:

```java
public class DataFilterFragment extends DialogFragment {

    public interface FilterListener {
        void onFilterChanged(DataFilter filter);
    }

    // ui elements
    private EditText etStartDate, etEndDate;
    private Button btnApply, btnClearFilters;
    private FloatingActionButton btnClose;

    private FilterListener filterListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_data_filter, container, false);
        initializeComponents(view);
        setupUI();
        return view;
    }

    private void initializeComponents(View view) {
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);
        btnClose = view.findViewById(R.id.btn_close);
        btnApply = view.findViewById(R.id.btn_apply);
        btnClearFilters = view.findViewById(R.id.btn_clear_filters);
    }

    private void setupUI() {
        btnClose.setOnClickListener(v -> dismiss());
        btnApply.setOnClickListener(v -> applyFilter());
        btnClearFilters.setOnClickListener(v -> clearFilters());
    }

    private void applyFilter() {
        DataFilter dataFilter = createDataFilter();
        if(dataFilter != null) {
            if(filterListener != null) {
                filterListener.onFilterChanged(dataFilter);
            }
            dismiss();
        }
    }

    private DataFilter createDataFilter() {
        String startDateString = etStartDate.getText().toString() + " 00:00";
        String endDateString = etEndDate.getText().toString() + " 00:00";

        // Check for empty fields
        if(startDateString.isEmpty() || endDateString.isEmpty()) {
            Toast.makeText(getActivity().getBaseContext(), "Please enter a start and an end date", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            Date startDate = parseDate(startDateString);
            Date endDate = parseDate(endDateString);

            if (startDate == null || endDate == null) {
                Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use " + DataFilter.TIMESTAMP_DATE_FORMAT + ".", Toast.LENGTH_SHORT).show();
                return null;
            }

            // Check that start date is before end date
            if(!startDate.before(endDate)) {
                Toast.makeText(getActivity().getBaseContext(), "Start date must be before the end date", Toast.LENGTH_SHORT).show();
                return null;
            }

            return new DataFilter(startDate, endDate);
        } catch(ParseException e) {
            Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use " + DataFilter.TIMESTAMP_DATE_FORMAT + ".", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private Date parseDate(String dateString) throws ParseException {
        // Define valid date format as being MM-dd-yy. Any other format will be rejected.
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DataFilter.TIMESTAMP_DATE_FORMAT, Locale.getDefault());
        simpleDateFormat.setLenient(false);

        return simpleDateFormat.parse(dateString);
    }

    private void clearFilters() {
        if(filterListener != null) {
            filterListener.onFilterChanged(null);
        }
        dismiss();
    }

    public void setFilterListener(FilterListener filterListener) {
        this.filterListener = filterListener;
    }
}
```

## Filter Application

### ThresholdHistoryActivity Implementation
The `ThresholdHistoryActivity` class implements the filter listener and applies the filter:

```java
private void openDataFilterFragment() {
    DataFilterFragment dataFilterFragment = new DataFilterFragment();
    dataFilterFragment.setFilterListener(new DataFilterFragment.FilterListener() {
        @Override
        public void onFilterChanged(DataFilter filter) {
            if(filter != null) {
                dataFilterHelper.setFilter(filter);
                dataFilterHelper.saveFilterState(ThresholdHistoryActivity.this);
                String msg = "Showing data from " + filter.getStartDate().toString() + " to " + filter.getEndDate().toString();
                Toast.makeText(ThresholdHistoryActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            else {
                dataFilterHelper.clearFilters();
            }
            loadThresholdBreaches();
        }
    });

    dataFilterFragment.show(getSupportFragmentManager(), "dataFilterFragment");
}
```

The activity also initializes the filter on startup:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_threshold_history);
    
    initializeComponents();
    setupToolbar();
    setupListeners();
    loadThresholdBreaches();
    DataFilterHelper.getInstance().restoreFilterPreferences(this);
}
```

The loadThresholdBreaches method is called to load data based on the current filter:

```java
private void loadThresholdBreaches() {
    // Optimize data loading for Android 12
    databaseHelper.getThresholdBreachesWithCustomThresholds(this)
        .observe(this, sensorDataList -> {
            if (sensorDataList != null && !sensorDataList.isEmpty()) {
                recyclerView.setVisibility(View.VISIBLE);
                tvNoData.setVisibility(View.GONE);
                adapter.setBreaches(sensorDataList);
                btnDeleteAll.setVisibility(View.VISIBLE);
                fabSelect.show();
                
                // Add optimization for redraw
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    recyclerView.post(() -> recyclerView.invalidateItemDecorations());
                }
            } else {
                recyclerView.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
                btnDeleteAll.setVisibility(View.GONE);
                fabSelect.hide();
                
                // Exit selection mode if active
                if (isInSelectionMode) {
                    toggleSelectionMode();
                }
            }
            
            // Invalidate options menu to update action items
            invalidateOptionsMenu();
        });
} 