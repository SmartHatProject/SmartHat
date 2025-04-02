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
