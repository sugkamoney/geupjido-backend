package com.geupjido.report.service;

public class WeeklyReportNotFoundException extends RuntimeException {
	public WeeklyReportNotFoundException(long reportId) {
		super("공개된 리포트를 찾을 수 없습니다: " + reportId);
	}
}
