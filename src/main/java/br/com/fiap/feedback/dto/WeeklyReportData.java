package br.com.fiap.feedback.dto;

import java.time.LocalDate;
import java.util.List;

public class WeeklyReportData {

    public LocalDate startDate;
    public LocalDate endDate;
    public int total;
    public double averageScore;
    public long highUrgency;
    public long mediumUrgency;
    public long lowUrgency;
    public List<DayReport> dailyReports;

    public static class DayReport {
        public LocalDate date;
        public int count;
        public double averageScore;
        public long urgentCount;

        public DayReport(LocalDate date, int count, double averageScore, long urgentCount) {
            this.date = date;
            this.count = count;
            this.averageScore = averageScore;
            this.urgentCount = urgentCount;
        }
    }
}
