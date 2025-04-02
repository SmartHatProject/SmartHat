package com.team12.smarthat.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.team12.smarthat.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataFilterFragment extends DialogFragment {
    EditText et_start_date, et_end_date;
    Button btn_apply, btn_clear_filters;
    FloatingActionButton btn_close;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View view = inflater.inflate(R.layout.fragment_data_filter, container, false);

        initializeComponents(view);
        setupUI();

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

    }

    private void initializeComponents(View view) {
        et_start_date = view.findViewById(R.id.et_start_date);
        et_end_date = view.findViewById(R.id.et_end_date);
        btn_close = view.findViewById(R.id.btn_close);
        btn_apply = view.findViewById(R.id.btn_apply);
        btn_clear_filters = view.findViewById(R.id.btn_clear_filters);
    }

    private void setupUI() {
        btn_close.setOnClickListener(v -> dismiss());
        btn_apply.setOnClickListener(v -> applyFilter());
        btn_clear_filters.setOnClickListener(v -> dismiss());
    }

    private void applyFilter() {
        if(validateDates(et_start_date.getText().toString(), et_end_date.getText().toString())) {
            Toast.makeText(getActivity().getBaseContext(), "Applying filters...", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private boolean validateDates(String startDateString, String endDateString) {

        // Check for empty fields
        if(startDateString.isEmpty() || endDateString.isEmpty()) {
            Toast.makeText(getActivity().getBaseContext(), "Please enter a start and an end date", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Define valid date format as being dd-MM-yyyy. Any other format will be rejected.
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        simpleDateFormat.setLenient(false);

        try {
            Date startDate = simpleDateFormat.parse(startDateString);
            Date endDate = simpleDateFormat.parse(endDateString);

            if (startDate == null || endDate == null) {
                Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use dd-MM-yyyy.", Toast.LENGTH_SHORT).show();
                return false;
            }

            // Check that start date is before end date
            if(!startDate.before(endDate)) {
                Toast.makeText(getActivity().getBaseContext(), "Start date must be before the end date", Toast.LENGTH_SHORT).show();
                return false;
            }

            return true;
        } catch(ParseException e) {
            Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use dd-MM-yyyy.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
