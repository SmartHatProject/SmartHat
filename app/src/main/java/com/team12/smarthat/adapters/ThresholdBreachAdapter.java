package com.team12.smarthat.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team12.smarthat.R;
import com.team12.smarthat.models.SensorData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ThresholdBreachAdapter extends RecyclerView.Adapter<ThresholdBreachAdapter.ViewHolder> {
    
    private List<SensorData> breaches;
    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());
    
    public ThresholdBreachAdapter(Context context) {
        this.context = context;
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
        
        // Configure icon based on sensor type
        holder.ivSensorIcon.setImageResource(
            isDustSensor ? android.R.drawable.ic_menu_compass : android.R.drawable.ic_lock_silent_mode_off
        );
        holder.ivSensorIcon.setColorFilter(
            context.getResources().getColor(
                isDustSensor ? android.R.color.holo_blue_dark : android.R.color.holo_orange_dark
            )
        );
        
        // Format sensor type
        String sensorTypeText = isDustSensor ? "Dust Sensor" : "Noise Sensor";
        holder.tvSensorType.setText(sensorTypeText);
        
        // Format sensor value with unit
        String valueWithUnit = isDustSensor
                ? String.format(Locale.getDefault(), "%.1f µg/m³", data.getValue())
                : String.format(Locale.getDefault(), "%.1f dB", data.getValue());
        holder.tvSensorValue.setText(valueWithUnit);
        
        // Format timestamp
        String formattedDate = dateFormat.format(new Date(data.getTimestamp()));
        holder.tvTimestamp.setText(formattedDate);
    }
    
    @Override
    public int getItemCount() {
        return breaches == null ? 0 : breaches.size();
    }
    
    public void setBreaches(List<SensorData> breaches) {
        this.breaches = breaches;
        notifyDataSetChanged();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivSensorIcon;
        private final TextView tvSensorType;
        private final TextView tvSensorValue;
        private final TextView tvTimestamp;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSensorIcon = itemView.findViewById(R.id.iv_sensor_icon);
            tvSensorType = itemView.findViewById(R.id.tv_sensor_type);
            tvSensorValue = itemView.findViewById(R.id.tv_sensor_value);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
        }
    }
} 