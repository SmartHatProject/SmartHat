package com.team12.smarthat.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.team12.smarthat.R;
import com.team12.smarthat.models.DataFilter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
     
        String startDateRaw = etStartDate.getText().toString().trim();
        String endDateRaw = etEndDate.getText().toString().trim();
        
        
        if(startDateRaw.isEmpty() || endDateRaw.isEmpty()) {
            Toast.makeText(getActivity().getBaseContext(), "Please enter a start and an end date", Toast.LENGTH_SHORT).show();
            return null;
        }
        
        
        try {
            
            String startDateString = startDateRaw.replace("-", "/") + " 00:00";
            String endDateString = endDateRaw.replace("-", "/") + " 23:59";
            
            Date startDate = parseDate(startDateString);
            Date endDate = parseDate(endDateString);

            if (startDate == null || endDate == null) {
                Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use MM-DD-YY format.", Toast.LENGTH_SHORT).show();
                return null;
            }

           
            if(!startDate.before(endDate)) {
                Toast.makeText(getActivity().getBaseContext(), "Start date must be before the end date", Toast.LENGTH_SHORT).show();
                return null;
            }

            return new DataFilter(startDate, endDate);
        } catch(ParseException e) {
            Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use MM-DD-YY format.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private Date parseDate(String dateString) throws ParseException {
      
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