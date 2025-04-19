package com.team12.smarthat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.ParseException;
import com.team12.smarthat.models.DataFilter;

import static android.content.Context.MODE_PRIVATE;


public class DataFilterHelper {
    private static DataFilterHelper instance;
    private DataFilter currentFilter;
    
    
    private DataFilterHelper() {
        currentFilter = null;
    }
    
    /**
     * Get the singleton instance
     * @return DataFilterHelper instance
     */
    public static DataFilterHelper getInstance() {
        if (instance == null) {
            instance = new DataFilterHelper();
        }
        return instance;
    }
    
    /**
     * Get the current active filter
     * @return DataFilter object or null if no filter is active
     */
    public DataFilter getCurrentFilter() {
        return currentFilter;
    }
    
    /**
     * Set a new date filter
     * @param dataFilter DataFilter to set as current
     */
    public void setFilter(DataFilter dataFilter) {
        currentFilter = dataFilter;
    }
    
    /**
     * Clear the active filter
     */
    public void clearFilters() {
        this.currentFilter = null;
    }
    
    /**
     * Save filter state to sharedPreferences
     * @param context Context for accessing shared preferences
     */
    public void saveFilterState(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
        
        if(currentFilter != null) {
            prefsEditor.putString(Constants.PREF_FILTER_START_DATE, currentFilter.getStartDate().toString());
            prefsEditor.putString(Constants.PREF_FILTER_END_DATE, currentFilter.getEndDate().toString());
        } else {
            // Clear saved filters when currentFilter is null
            prefsEditor.remove(Constants.PREF_FILTER_START_DATE);
            prefsEditor.remove(Constants.PREF_FILTER_END_DATE);
        }
        
        prefsEditor.apply();
    }

    /**
     * Retrieve filter state from sharedPreferences
     * @param context Context for accessing shared preferences
     */
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
} 