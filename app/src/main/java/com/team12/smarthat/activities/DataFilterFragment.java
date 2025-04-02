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
import com.team12.smarthat.models.DataFilter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataFilterFragment extends DialogFragment {
    private EditText et_start_date, et_end_date;
    private Button btn_apply, btn_clear_filters;
    private FloatingActionButton btn_close;

    private final String TIMESTAMP_DATE_FORMAT = "MM/dd/yy";

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
        DataFilter dataFilter = createDataFilter();
        if(dataFilter != null) {
            Toast.makeText(getActivity().getBaseContext(), "Applying filters...", Toast.LENGTH_SHORT).show();
            dismiss();
        }
    }

    private DataFilter createDataFilter() {
        String startDateString = et_start_date.getText().toString();
        String endDateString = et_end_date.getText().toString();

        // Check for empty fields
        if(startDateString.isEmpty() || endDateString.isEmpty()) {
            Toast.makeText(getActivity().getBaseContext(), "Please enter a start and an end date", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            Date startDate = parseDate(startDateString);
            Date endDate = parseDate(endDateString);

            if (startDate == null || endDate == null) {
                Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use " + TIMESTAMP_DATE_FORMAT + ".", Toast.LENGTH_SHORT).show();
                return null;
            }

            // Check that start date is before end date
            if(!startDate.before(endDate)) {
                Toast.makeText(getActivity().getBaseContext(), "Start date must be before the end date", Toast.LENGTH_SHORT).show();
                return null;
            }

            return new DataFilter(startDate, endDate);
        } catch(ParseException e) {
            Toast.makeText(getActivity().getBaseContext(), "Invalid date format. Please use " + TIMESTAMP_DATE_FORMAT + ".", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private Date parseDate(String dateString) throws ParseException {
        // Define valid date format as being MM-dd-yy. Any other format will be rejected.
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TIMESTAMP_DATE_FORMAT, Locale.getDefault());
        simpleDateFormat.setLenient(false);

        return simpleDateFormat.parse(dateString);
    }
}
