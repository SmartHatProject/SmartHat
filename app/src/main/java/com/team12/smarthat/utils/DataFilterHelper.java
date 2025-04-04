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
                currentFilter = new DataFilter(startDate, endDate);
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