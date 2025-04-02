package com.team12.smarthat.utils;

import com.team12.smarthat.models.DataFilter;

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

    // Save filter state to sharedPreferences

    // Retrieve filter state from sharedPreferences

    // Build SQL query string

    // Clear filters
}
