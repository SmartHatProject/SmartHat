package com.team12.smarthat.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.team12.smarthat.R;
import com.team12.smarthat.models.SensorData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ThresholdBreachAdapter extends RecyclerView.Adapter<ThresholdBreachAdapter.ViewHolder> {
    
    private List<SensorData> breaches;
    private final Context context;
    private final SimpleDateFormat dateFormat;
    
    // Track selection state
    private boolean selectionMode = false;
    private final Set<Integer> selectedItems = new HashSet<>();
    
    public ThresholdBreachAdapter(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_threshold_breach, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (breaches == null || breaches.isEmpty()) {
            return;
        }
        
        SensorData data = breaches.get(position);
        boolean isDustSensor = data.getSensorType().equals("dust");
        
        // Selection checkbox visibility and state
        holder.cbSelectItem.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelectItem.setChecked(selectedItems.contains(data.getId()));
        
        // Apply lighter highlight to selected items for better performance
        if (selectionMode && selectedItems.contains(data.getId())) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_container));
        } else {
            holder.itemView.setBackground(null);
        }
        
        // Set up click listeners - optimized for Android 12
        setupClickListeners(holder, data, position);
        
        // Set sensor icon
        int iconResource = isDustSensor ? android.R.drawable.ic_menu_compass : android.R.drawable.ic_lock_silent_mode_off;
        holder.ivSensorIcon.setImageResource(iconResource);
        
        // Set icon color
        int iconColor = ContextCompat.getColor(context, 
            isDustSensor ? R.color.primary : R.color.secondary);
        holder.ivSensorIcon.setColorFilter(iconColor);
        
        // Set texts
        holder.tvSensorType.setText(isDustSensor ? "Dust" : "Noise");
        
        // Format value
        String valueWithUnit = formatSensorValue(data);
        holder.tvSensorValue.setText(valueWithUnit);
        
        // Format timestamp
        String formattedDate = dateFormat.format(new Date(data.getTimestamp()));
        holder.tvTimestamp.setText(formattedDate);
        
        // Set threshold status
        TextView thresholdStatus = holder.tvThresholdStatus;
        thresholdStatus.setText(R.string.threshold_exceeded);
        thresholdStatus.setBackgroundResource(R.drawable.bg_threshold_chip);
    }
    
    private void setupClickListeners(ViewHolder holder, SensorData data, int position) {
        View.OnClickListener clickListener = v -> {
            if (selectionMode) {
                toggleSelection(data.getId());
                notifyItemChanged(position);
            }
        };
        
        // Optimize touch events for Android 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            holder.itemView.setOnClickListener(clickListener);
            holder.cbSelectItem.setOnClickListener(clickListener);
        } else {
            holder.itemView.setOnClickListener(clickListener);
            holder.cbSelectItem.setOnClickListener(clickListener);
        }
    }
    
    private String formatSensorValue(SensorData data) {
        boolean isDustSensor = data.getSensorType().equals("dust");
        String unit = isDustSensor ? " µg/m³" : " dB";
        return String.format(Locale.US, "%.1f%s", data.getValue(), unit);
    }
    
    @Override
    public int getItemCount() {
        return breaches == null ? 0 : breaches.size();
    }
    
    public void setBreaches(List<SensorData> breaches) {
        this.breaches = breaches;
        notifyDataSetChanged();
    }
    
    /**
     * Toggle selection mode
     * @param enabled true to enable selection mode, false to disable
     */
    public void setSelectionMode(boolean enabled) {
        if (this.selectionMode != enabled) {
            this.selectionMode = enabled;
            if (!enabled) {
                selectedItems.clear();
            }
            notifyDataSetChanged();
        }
    }
    
    /**
     * Toggle item selection
     * @param id ID of the item to toggle
     */
    private void toggleSelection(int id) {
        if (selectedItems.contains(id)) {
            selectedItems.remove(id);
        } else {
            selectedItems.add(id);
        }
    }
    
    /**
     * Get all selected item IDs
     * @return List of selected IDs
     */
    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedItems);
    }
    
    /**
     * Check if any items are selected
     * @return true if there are selected items
     */
    public boolean hasSelections() {
        return !selectedItems.isEmpty();
    }
    
    /**
     * Clear all selections
     */
    public void clearSelections() {
        selectedItems.clear();
        notifyDataSetChanged();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox cbSelectItem;
        private final ImageView ivSensorIcon;
        private final TextView tvSensorType;
        private final TextView tvSensorValue;
        private final TextView tvTimestamp;
        private final TextView tvThresholdStatus;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelectItem = itemView.findViewById(R.id.cb_select_item);
            ivSensorIcon = itemView.findViewById(R.id.iv_sensor_icon);
            tvSensorType = itemView.findViewById(R.id.tv_sensor_type);
            tvSensorValue = itemView.findViewById(R.id.tv_sensor_value);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvThresholdStatus = itemView.findViewById(R.id.tv_threshold_status);
        }
    }
} 