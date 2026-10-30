package com.company.crm.common;

public record ApiResponse<T>(String status, String message, T data) {
  public static <T> ApiResponse<T> ok(String m, T d) {
    return new ApiResponse<>("success", m, d);
  }
}
