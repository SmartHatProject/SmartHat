package com.team12.smarthat.activities;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.team12.smarthat.R;
import com.team12.smarthat.adapters.ThresholdBreachAdapter;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.DataFilter;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.DataFilterHelper;

import java.util.List;

public class ThresholdHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvNoData;
    private ThresholdBreachAdapter adapter;
    private DatabaseHelper databaseHelper;
    private LinearLayout actionButtonsLayout;
    private Button btnCancelSelection, btnDeleteSelected, btnDeleteAll;
    private FloatingActionButton fabSelect;
    private boolean isInSelectionMode = false;
    private Toolbar toolbar;
    private DataFilterHelper dataFilterHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_threshold_history);
        
        // Initialize the data filter helper
        dataFilterHelper = DataFilterHelper.getInstance();
        // Restore any saved filter preferences
        dataFilterHelper.restoreFilterPreferences(this);
        
        initializeComponents();
        setupToolbar();
        setupListeners();
        loadThresholdBreaches();
    }
    
    private void setupToolbar() {
        toolbar = findViewById(R.id.threshold_history_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }
    
    private void initializeComponents() {
        recyclerView = findViewById(R.id.rv_threshold_history);
        tvNoData = findViewById(R.id.tv_no_data);
        actionButtonsLayout = findViewById(R.id.action_buttons_layout);
        btnCancelSelection = findViewById(R.id.btn_cancel_selection);
        btnDeleteSelected = findViewById(R.id.btn_delete_selected);
        btnDeleteAll = findViewById(R.id.btn_delete_all);
        fabSelect = findViewById(R.id.fab_select);
        
        // Optimize recyclerview for Pixel 4a on Android 12
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        
        // Disable expensive animations for better performance
        recyclerView.setItemAnimator(null);
        
        // Set up adapter with optimized configuration
        adapter = new ThresholdBreachAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // Optimize scroll performance for Android 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // Enable prefetch for smoother scrolling
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).setInitialPrefetchItemCount(4);
            }
        }
        
        // Use singleton pattern for database access
        databaseHelper = DatabaseHelper.getInstance();
    }
    
    private void setupListeners() {
        // FAB click listener
        fabSelect.setOnClickListener(v -> toggleSelectionMode());
        
        // Cancel button click listener
        btnCancelSelection.setOnClickListener(v -> toggleSelectionMode());
        
        // Delete button click listener
        btnDeleteSelected.setOnClickListener(v -> {
            if (adapter.hasSelections()) {
                showDeleteConfirmationDialog();
            } else {
                Snackbar.make(recyclerView, "No items selected", Snackbar.LENGTH_SHORT).show();
            }
        });
        
        // Delete All button click listener
        btnDeleteAll.setOnClickListener(v -> showDeleteAllConfirmationDialog());
    }
    
    private void toggleSelectionMode() {
        isInSelectionMode = !isInSelectionMode;
        
        // Update UI
        actionButtonsLayout.setVisibility(isInSelectionMode ? View.VISIBLE : View.GONE);
        btnDeleteAll.setVisibility(isInSelectionMode ? View.GONE : View.VISIBLE);
        
        // Change FAB icon with animation for Android 12 style
        fabSelect.animate().scaleX(0.5f).scaleY(0.5f).setDuration(100).withEndAction(() -> {
            fabSelect.setImageResource(isInSelectionMode 
                    ? R.drawable.ic_close
                    : R.drawable.ic_edit_select);
            fabSelect.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
        }).start();
        
        // Update adapter
        adapter.setSelectionMode(isInSelectionMode);
        
        // Clear selections when exiting selection mode
        if (!isInSelectionMode) {
            adapter.clearSelections();
        }
    }
    
    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Delete Selected")
                .setMessage("Are you sure you want to delete the selected items? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedItems())
                .setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Style the buttons with Material You colors for Android 12
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.error));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.primary));
        });
        
        dialog.show();
    }
    
    private void showDeleteAllConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Delete All")
                .setMessage("Are you sure you want to delete all threshold breach records? This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    databaseHelper.deleteAllThresholdBreaches(this);
                    Snackbar.make(recyclerView, "All records deleted", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Style the buttons with Material You colors for Android 12
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.error));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.primary));
        });
        
        dialog.show();
    }
    
    private void deleteSelectedItems() {
        List<Integer> selectedIds = adapter.getSelectedIds();
        
        if (!selectedIds.isEmpty()) {
            databaseHelper.deleteThresholdBreaches(selectedIds);
            toggleSelectionMode(); // Exit selection mode
            
            // Use Snackbar for Material Design 3 style feedback
            Snackbar.make(recyclerView, 
                    selectedIds.size() + " items deleted", 
                    Snackbar.LENGTH_SHORT).show();
        }
    }
    
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
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_threshold_history, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        else if(id == R.id.action_filter) {
            openDataFilterFragment();
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void openDataFilterFragment() {
        DataFilterFragment dataFilterFragment = new DataFilterFragment();
        dataFilterFragment.setFilterListener(new DataFilterFragment.FilterListener() {
            @Override
            public void onFilterChanged(DataFilter filter) {
                if(filter != null) {
                    dataFilterHelper.setFilter(filter);
                    // Save filter state
                    dataFilterHelper.saveFilterState(ThresholdHistoryActivity.this);
                    String msg = "Showing data from " + filter.getFormattedStartDate() + " to " + filter.getFormattedEndDate();
                    Toast.makeText(ThresholdHistoryActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
                else {
                    dataFilterHelper.clearFilters();
                    // Save filter state (which is now null)
                    dataFilterHelper.saveFilterState(ThresholdHistoryActivity.this);
                }
                loadThresholdBreaches();
            }
        });

        dataFilterFragment.show(getSupportFragmentManager(), "dataFilterFragment");
    }
} 